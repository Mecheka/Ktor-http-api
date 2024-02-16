package com.example.plugins

import com.example.modules.UnauthorizedException
import com.example.routes.customerRouting
import com.example.routes.getOrderRoute
import com.example.routes.listOrdersRoute
import com.example.routes.totalizeOrderRoute
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.resources.Resources
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.Serializable
import org.slf4j.event.Level

fun Application.configureRouting() {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(Resources)
    install(AutoHeadResponse)
    install(StatusPages) {
        exception<RequestValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.reasons.joinToString())
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            val retryAfter = call.response.headers["Retry-After"]
            call.respondText(text = "429: Too many requests. Wait for $retryAfter seconds.", status = status)
        }
        exception<UnauthorizedException> { call, cause ->
            call.respondText(cause.message!!)
        }
    }
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        meterBinders = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
            FileDescriptorMetrics(),
            UptimeMetrics()
        )
    }
    routing {
        customerRouting()
        listOrdersRoute()
        getOrderRoute()
        totalizeOrderRoute()
        articlesRouting()
        validateRouting()
        get("/metrics") {
            call.respond(appMicrometerRegistry.scrape())
        }
    }
}

fun Route.articlesRouting() {
    get<Articles.Id> {
//        call.application.log.info("An article with id ${it.id}")
        call.respondText("An article with id ${it.id}")
    }
    get<Articles> { articles ->
        articles.id?.let {
            call.respondText("An article with id $it")
        } ?: run {
            call.respondText("No query id")
        }
    }
}

fun Route.validateRouting() {
    install(RequestValidation) {
        validate<String> { bodyText ->
            if (!bodyText.startsWith("Hello"))
                ValidationResult.Invalid("Body text should start with 'Hello'")
            else ValidationResult.Valid
        }

        validate<Customer> { customer ->
            if (customer.id <= 0)
                ValidationResult.Invalid("A customer ID should be greater than 0")
            else ValidationResult.Valid
        }

        validate {
            filter { body ->
                body is ByteArray
            }
            validation { body ->
                check(body is ByteArray)
                val intValue = String(body).toInt()
                if (intValue <= 0)
                    ValidationResult.Invalid("A value should be greater than 0")
                else ValidationResult.Valid
            }
        }
    }

    post("/text") {
        val body = call.receive<String>()
        call.respond(body)
    }
    post("/json") {
        val customer = call.receive<Customer>()
        call.respond("Customer id is ${customer.id}")
    }
    post("/array") {
        val body = call.receive<ByteArray>()
        call.respond(String(body))
    }
}

@Serializable
@Resource("/articles")
class Articles(val id: Int? = null) {
    @Resource("{id}")
    class Id(val parent: Articles = Articles(), val id: Int)
}

@Serializable
data class Customer(val id: Int, val firstName: String, val lastName: String)