/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package al.clk.gui.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Respond with JSON
 */
suspend inline fun <reified T : Any> ApplicationCall.respondJson(data: T) {
    response.headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    respond(Json.encodeToString(data))
}

/**
 * Respond with error
 */
suspend fun ApplicationCall.respondError(status: HttpStatusCode, message: String) {
    respond(status, mapOf(
        "error" to status.description,
        "message" to message,
        "status" to status.value
    ))
}

/**
 * Respond with validation error
 */
suspend fun ApplicationCall.respondValidationError(errors: Map<String, String>) {
    respond(HttpStatusCode.BadRequest, mapOf(
        "error" to "Validation failed",
        "errors" to errors
    ))
}

/**
 * Respond with created resource
 */
suspend inline fun <reified T : Any> ApplicationCall.respondCreated(data: T) {
    respond(HttpStatusCode.Created, data)
}

/**
 * Respond with no content
 */
suspend fun ApplicationCall.respondNoContent() {
    respond(HttpStatusCode.NoContent)
}
