package ru.cinimex.arch.demoCamelSagaService;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class ServiceRoute extends RouteBuilder {

    //Сервис является front-точкой входа. Стартует Saga и вызывает Service1 и Service2 как учестников Saga.
    //В случае ошибок в одном из сервисов ожидается автоматическое выполнение compensate во всех сервисах

    @Override
    public void configure() throws Exception {
        restConfiguration().component("undertow").host("localhost").port(8888).bindingMode(RestBindingMode.off);

        rest("/api/")
                .post("{flow}")                //flow={ok,fault,timeout}.
                .route()

                .saga()
                    .propagation(SagaPropagation.REQUIRED)
                    .timeout(5,TimeUnit.SECONDS)     //по истечении 5sec будет выплнен compensate, если saga не будет завершена
                    // business-logic here
                    .log("Executing Saga #${header.Long-Running-Action}, flow= ${header.flow}")
                    .log("calling Service1")
                    .removeHeaders("CamelHttp*")
                    .toD("http4://{{service1_host}}/api/${header.flow}")
                    .log("calling Service2")
                    .toD("http4://{{service2_host}}/api/${header.flow}")    //в данном сервисе логика обработки flow.
                    .to("direct:MainServiceLogic")
                 .end();

        from("direct:MainServiceLogic")
                .log("**** service logic");

    }
}
