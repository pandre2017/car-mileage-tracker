package com.pandre.mileage.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiTest {
    @Test
    fun createUsesStableUuidIntegerAmountsAndBearerHeader() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"id":"entry","fuelDate":"2026-11-01","odometerKm":1200,"litersHundredths":4237,"totalPriceCents":7520,"createdByUid":"driver","createdAt":"2026-11-01T12:00:00Z"}"""
                    )
            )
            val api =
                Retrofit.Builder()
                    .baseUrl(server.url("/"))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(MileageApi::class.java)
            val result =
                api.create(
                    "Bearer fixture-token",
                    "car",
                    "entry",
                    FuelInput("2026-11-01", 1200, 4237, 7520),
                )
            val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            assertEquals("PUT", request.method)
            assertEquals("/v1/cars/car/fuel-entries/entry", request.path)
            assertEquals("Bearer fixture-token", request.getHeader("Authorization"))
            assertTrue(request.body.readUtf8().contains("\"litersHundredths\":4237"))
            assertEquals("SYNCED", result.entity("car").state)
        } finally {
            server.shutdown()
        }
    }
}
