package org.neobank.transactionservice.config;

import brave.Tracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.B3Propagation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BravePropagator;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class ZipkinConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${management.zipkin.tracing.endpoint:http://localhost:9411/api/v2/spans}")
    private String zipkinEndpoint;

    @Bean
    public zipkin2.reporter.brave.AsyncZipkinSpanHandler zipkinSpanHandler() {
        zipkin2.reporter.Sender sender = zipkin2.reporter.urlconnection.URLConnectionSender.create(zipkinEndpoint);
        return zipkin2.reporter.brave.AsyncZipkinSpanHandler.create(sender);
    }

    @Bean
    public Tracing braveTracing(zipkin2.reporter.brave.AsyncZipkinSpanHandler spanHandler) {
        return Tracing.newBuilder()
                .localServiceName(applicationName)
                .addSpanHandler(spanHandler)
                .propagationFactory(B3Propagation.FACTORY)
                .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder().build())
                .build();
    }

    @Bean
    public brave.Tracer braveTracer(Tracing tracing) {
        return tracing.tracer();
    }

    @Bean
    public BraveCurrentTraceContext braveCurrentTraceContext(Tracing tracing) {
        return new BraveCurrentTraceContext(tracing.currentTraceContext());
    }

    @Bean
    public BraveBaggageManager braveBaggageManager() {
        return new BraveBaggageManager();
    }

    @Bean
    public Tracer micrometerTracer(brave.Tracer braveTracer, BraveCurrentTraceContext currentTraceContext, BraveBaggageManager baggageManager) {
        return new BraveTracer(braveTracer, currentTraceContext, baggageManager);
    }

    @Bean
    public Propagator micrometerPropagator(Tracing tracing) {
        return new BravePropagator(tracing);
    }

    @Bean
    public ObservationHandler<io.micrometer.observation.Observation.Context> tracingObservationHandler(Tracer tracer, Propagator propagator) {
        return new TracingObservationHandler.FirstMatchingCompositeObservationHandler(
                new PropagatingReceiverTracingObservationHandler<>(tracer, propagator),
                new PropagatingSenderTracingObservationHandler<>(tracer, propagator),
                new DefaultTracingObservationHandler(tracer)
        );
    }

    @Bean
    public ObservationPredicate ignoreActuatorAndEurekaObservations() {
        return (name, context) -> {
            if (name.startsWith("spring.security.")) {
                return false;
            }
            if ("http.server.requests".equals(name) || name.contains("server")) {
                try {
                    Object carrier = context.getClass().getMethod("getCarrier").invoke(context);
                    String uri = "";
                    if (carrier != null) {
                        String className = carrier.getClass().getName();
                        if (className.contains("HttpServletRequest") || className.contains("RequestFacade")) {
                            uri = (String) carrier.getClass().getMethod("getRequestURI").invoke(carrier);
                        } else if (className.contains("ServerWebExchange")) {
                            Object request = carrier.getClass().getMethod("getRequest").invoke(carrier);
                            Object uriObj = request.getClass().getMethod("getURI").invoke(request);
                            uri = (String) uriObj.getClass().getMethod("getPath").invoke(uriObj);
                        } else if (className.contains("HttpRequest")) {
                            Object uriObj = carrier.getClass().getMethod("getURI").invoke(carrier);
                            uri = (String) uriObj.getClass().getMethod("getPath").invoke(uriObj);
                        }
                    }
                    if (uri != null && (uri.startsWith("/actuator") || uri.startsWith("/eureka"))) {
                        return false;
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
            if ("http.client.requests".equals(name) || name.contains("client")) {
                try {
                    Object carrier = context.getClass().getMethod("getCarrier").invoke(context);
                    if (carrier != null) {
                        Object uriObj = carrier.getClass().getMethod("getURI").invoke(carrier);
                        if (uriObj != null && uriObj.toString().contains("/eureka/")) {
                            return false;
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
            return true;
        };
    }
}
