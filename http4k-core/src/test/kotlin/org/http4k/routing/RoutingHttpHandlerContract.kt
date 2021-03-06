package org.http4k.routing

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.present
import org.http4k.core.Filter
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasHeader
import org.http4k.hamkrest.hasStatus
import org.junit.jupiter.api.Test

abstract class RoutingHttpHandlerContract {

    protected val validPath = "/route-contract"
    protected val prefix = "/prefix"
    private val prePrefix = "/pre-prefix"

    abstract val handler: RoutingHttpHandler

    open val expectedNotFoundBody = ""

    @Test
    fun `matches a particular route`() {
        val criteria = hasStatus(OK)

        assertThat(handler.matchAndInvoke(Request(GET, validPath)), present(criteria))
        assertThat(handler(Request(GET, validPath)), criteria)
    }

    @Test
    open fun `does not match a particular route`() {
        assertThat(handler.matchAndInvoke(Request(GET, "/not-found")), absent())
        assertThat(handler(Request(GET, "/not-found")), hasStatus(NOT_FOUND) and hasBody(expectedNotFoundBody))
    }

    @Test
    fun `with filter - applies to matching handler`() {
        val filtered = handler.withFilter(filterAppending("bar"))
        val criteria = hasStatus(OK) and hasHeader("res-header", "bar")
        val request = Request(GET, validPath)

        assertThat(filtered.matchAndInvoke(request), present(criteria))
        assertThat(filtered(request), criteria)
    }

    @Test
    open fun `with filter - applies when not found`() {
        val filtered = handler.withFilter(filterAppending("foo"))
        val request = Request(GET, "/not-found")

        assertThat(filtered.matchAndInvoke(request), absent())
        assertThat(filtered(request), hasStatus(NOT_FOUND) and hasHeader("res-header", "foo") and hasBody(expectedNotFoundBody))
    }

    @Test
    open fun `with filter - applies in correct order`() {
        val filtered = handler.withFilter(filterAppending("foo")).withFilter(filterAppending("bar"))
        val request = Request(GET, "/not-found")

        assertThat(filtered.matchAndInvoke(request), absent())
        assertThat(filtered(request), hasStatus(NOT_FOUND) and hasHeader("res-header", "foobar"))
    }

    @Test
    fun `with base path - matches`() {
        val withBase = handler.withBasePath(prefix)
        val request = Request(GET, "$prefix$validPath")
        val criteria = hasStatus(OK)

        assertThat(withBase.matchAndInvoke(request), present(criteria))
        assertThat(withBase(request), criteria)
    }

    @Test
    open fun `with base path - no longer matches original`() {
        val withBase = handler.withBasePath(prefix)
        val request = Request(GET, validPath)

        assertThat(withBase.matchAndInvoke(request), absent())
        assertThat(withBase(request), hasStatus(NOT_FOUND))
    }

    @Test
    fun `with base path - multiple levels`() {
        val withBase = handler.withBasePath(prefix).withBasePath(prePrefix)
        val request = Request(GET, "$prePrefix$prefix$validPath")
        val criteria = hasStatus(OK)

        assertThat(withBase.matchAndInvoke(request), present(criteria))
        assertThat(withBase(request), criteria)
    }

    protected fun RoutingHttpHandler.matchAndInvoke(request: Request) = match(request)?.invoke(request)

    protected fun filterAppending(value: String) = Filter { next ->
        {
            val response = next(it)
            response.replaceHeader("res-header", response.header("res-header").orEmpty() + value)
        }
    }
}