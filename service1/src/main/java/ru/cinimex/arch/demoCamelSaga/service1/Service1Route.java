package ru.cinimex.arch.demoCamelSaga.service1;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class Service1Route extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        restConfiguration().component("undertow").host("localhost").port(8081).bindingMode(RestBindingMode.off);

        rest("/api/")
            .post("{flow}")        //saga имеет смысл для операций, изменяющих состояние
                .route()
                .setBody(simple("888888"))              //!!! не попадет в saga!
                .log("****** body = ${body}")
                .setHeader("zzzzzz",constant("88888888")) // !!! не попадет в saga
                .log("****** header.zzzzzz = ${header.zzzzzz}")

                //поддержка Saga. Нужен coordinator (отдельное app) и сервис взаимодействия: camel-lra-starter + camel-undertow-starter, конфиг в application.yml)
                .saga()
                    .timeout(5,TimeUnit.SECONDS)         //по истечении 5sec будет выплнен compensate, если saga не будет завершена
                    .propagation(SagaPropagation.REQUIRED)      //start Saga если нет и join если есть saga_id в заголовке (Long-Running-Action)
                /*в option можно указать нужные для compensate бизнес-данные. Уйдет в coordinator как query параметр compensate endpoint
                  если body={'business_id':'1234'}, то сформирует линк:
                  http://localhost:8081/lra-participant/compensate?business_data=%7B%27business_id%27%3A%271234%27%7D&id=&Camel-Saga-Compensate=direct%3A%2F%2FcancelService1 */
                    .setBody(simple("777777"))              //!!! не попадет в saga!
                    .log("****** body = ${body}")
                    .option("business_data", body())
                    .option("flow", header("flow"))
                    .compensation("direct:cancelService1")
                    .completion("direct:completion")
                    .to("direct:Service1Logic")         //бизнес-логика
                 //   .removeHeaders("CamelHttp*")
                 //   .toD("http4://{{service2_host}}/api/${header.flow}")   //service2 вызывается как участник Saga.

        .end();

        from("direct:completion")
                .log("****completion Service1");


        from("direct:Service1Logic")
                .log("*****executing logic in Service1 with ${body} and flow = ${header.flow}")
            ;

        from("direct:cancelService1")
                .transform().header("business_data")            // выше мы поместили бизнес-данные в параметр body
//                .bean(orderManagerService, "cancelOrder")         //тут должна быть compensate логика
                .log("*****compensating Service1 with business data = ${body} and flow = ${header.flow}");
    }
}
