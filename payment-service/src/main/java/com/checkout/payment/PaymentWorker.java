package com.checkout.payment;

import com.checkout.common.AllocateCommand;
import com.checkout.common.AuthorizeCommand;
import com.checkout.common.CardRules;
import com.checkout.common.Channels;
import com.checkout.common.PaymentEvent;
import com.checkout.common.StockEvent;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentWorker {

    private final HoldRepository holds;
    private final KafkaTemplate<String, Object> kafka;
    private final RabbitTemplate rabbit;

    public PaymentWorker(HoldRepository holds, KafkaTemplate<String, Object> kafka, RabbitTemplate rabbit) {
        this.holds = holds;
        this.kafka = kafka;
        this.rabbit = rabbit;
    }

    @RabbitListener(queues = Channels.AUTHORIZE, concurrency = "4")
    @Transactional
    public void onAuthorize(AuthorizeCommand command) {
        if (holds.existsById(command.orderId())) {
            return;
        }
        if (CardRules.declined(command.cardToken())) {
            Publish.kafka(kafka, Channels.PAYMENTS, command.orderId(),
                    new PaymentEvent(command.orderId(), "DECLINED", command.amountCents()));
            throw new AmqpRejectAndDontRequeueException("card declined");
        }
        HoldEntity hold = new HoldEntity();
        hold.setOrderId(command.orderId());
        hold.setAmountCents(command.amountCents());
        hold.setStatus("AUTHORIZED");
        holds.save(hold);
        Publish.afterCommit(() -> {
            Publish.kafka(kafka, Channels.PAYMENTS, command.orderId(),
                    new PaymentEvent(command.orderId(), "AUTHORIZED", command.amountCents()));
            rabbit.convertAndSend(Channels.ALLOCATE, new AllocateCommand(
                    command.orderId(), command.sku(), command.quantity(), command.amountCents()));
        });
    }

    @KafkaListener(topics = Channels.STOCK, groupId = "payment-release")
    @Transactional
    public void onStock(StockEvent event) {
        if (!"RELEASED".equals(event.status())) {
            return;
        }
        if (holds.release(event.orderId()) == 1) {
            return;
        }
        HoldEntity hold = holds.findById(event.orderId()).orElse(null);
        if (hold == null || "AUTHORIZED".equals(hold.getStatus())) {
            throw new IllegalStateException("hold not ready for " + event.orderId());
        }
    }
}
