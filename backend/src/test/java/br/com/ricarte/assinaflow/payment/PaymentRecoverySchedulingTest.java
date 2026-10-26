package br.com.ricarte.assinaflow.payment;

import br.com.ricarte.assinaflow.config.SchedulingConfig;
import br.com.ricarte.assinaflow.subscription.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRecoverySchedulingTest {
    @Test void recoveryRunsWithGeneralSchedulersDisabled() {
        var payments=mock(PaymentService.class);
        var called=new CountDownLatch(1);
        when(payments.recover(100)).thenAnswer(inv->{ called.countDown();return 0; });
        new ApplicationContextRunner().withUserConfiguration(SchedulingConfig.class,PaymentRecoveryScheduler.class)
                .withBean(PaymentService.class,()->payments)
                .withPropertyValues("app.scheduler.enabled=false","app.payments.recovery.enabled=true","app.payments.recovery.delay-ms=60000")
                .run(context->{
                    assertThat(context).hasSingleBean(PaymentRecoveryScheduler.class);
                    assertThat(called.await(5,TimeUnit.SECONDS)).isTrue();
                    verify(payments).recover(100);
                });
    }
    @Test void disablingRecoveryRegistersNoFinancialTask() {
        var payments=mock(PaymentService.class);
        new ApplicationContextRunner().withUserConfiguration(SchedulingConfig.class,PaymentRecoveryScheduler.class)
                .withBean(PaymentService.class,()->payments)
                .withPropertyValues("app.scheduler.enabled=false","app.payments.recovery.enabled=false")
                .run(context->{
                    assertThat(context).doesNotHaveBean(PaymentRecoveryScheduler.class);
                    assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
                    verifyNoInteractions(payments);
                });
    }
}
