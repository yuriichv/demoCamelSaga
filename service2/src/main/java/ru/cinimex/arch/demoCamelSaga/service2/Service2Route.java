package ru.cinimex.arch.demoCamelSaga.service2;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;


@Component
public class Service2Route extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        restConfiguration().component("undertow").host("localhost").port(8082).bindingMode(RestBindingMode.off);

        rest("/api/").post("{flow}")
                .route()
                .saga()                                           //конфигурация Saga
                    .propagation(SagaPropagation.SUPPORTS)        //не будет выполнять start, но будет join при наличии saga_id header
                   // .timeout(5,TimeUnit.SECONDS)           //!!! если timeout не задан явно для текущего сервиса, не будет работать , даже если был задан в другом
                    .option("business_data", body())        //бизнес-данные для compensate берем из body()
                    .option("flow", header("flow"))
                    .compensation("direct:cancelService2")
                    //business-logic here
                     .to("direct:service2Logic");


        from("direct:service2Logic")
                .log("*****executing logic in Service2 with body=${body} and flow = ${header.flow}")
                .choice()
                    .when(header("flow").isEqualTo("fault"))
                        .setHeader(Exchange.HTTP_RESPONSE_CODE,constant("500"))  //ожидаем выполнение compensate service2 и service1
                        .setFaultBody(constant("fault flow executed"))
                //.throwException(new Exception("fault flow executed"))
                    .when(header("flow").isEqualTo("timeout"))
                        .delay(10000).endChoice()                                        //ожидаем выполнение compensate service2 и service1
                    .otherwise()
                        .setHeader(Exchange.HTTP_RESPONSE_CODE,constant("200"))      //ожидаем успешное завершение Saga
                        .setBody(constant("{\"response\":\"service2 response\"}"))
        ;


        from("direct:cancelService2")
                .log("*****compensating Service2 with flow = ${header.flow} and saga id = ${header.Long-Running-Action}"  );
    }
}
