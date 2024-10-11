package com.punchthrough.blestarterappandroid
import kotlin.random.Random
import fi.iki.elonen.NanoHTTPD


class CsvHttpServer(port: Int) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        try {
            val uri = session.uri
            val method = session.method
            println("Received request for URI: $uri with method: $method")

            // Check for POST method and the correct URI
            if (uri == "/lastvalue" && method == Method.POST) {
                // Generate a random value to return
                val randomValue = Random.nextInt(0, 100).toString()
                println("Generated random value: $randomValue")

                // Return the response with the random value
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "Random value: $randomValue")
            } else {
                // Log if the request is not found
                println("Request not found: $uri with method: $method")
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Internal Server Error")
        }
    }
}
