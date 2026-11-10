package com.pandre.mileage.data

import retrofit2.http.*

data class Profile(val uid: String, val displayName: String, val email: String)
data class FuelInput(val fuelDate: String, val odometerKm: Long, val litersHundredths: Long, val totalPriceCents: Long)
data class FuelDto(val id: String, val fuelDate: String, val odometerKm: Long, val litersHundredths: Long, val totalPriceCents: Long, val createdByUid: String, val createdAt: String) {
    fun entity(carId: String) = EntryEntity(id, carId, fuelDate, odometerKm, litersHundredths, totalPriceCents, createdByUid, "SYNCED")
}
interface MileageApi {
    @GET("v1/me") suspend fun me(@Header("Authorization") auth: String): Profile
    @GET("v1/cars") suspend fun cars(@Header("Authorization") auth: String): List<CarEntity>
    @GET("v1/cars/{carId}/fuel-entries") suspend fun history(@Header("Authorization") auth: String, @Path("carId") carId: String): List<FuelDto>
    @PUT("v1/cars/{carId}/fuel-entries/{id}") suspend fun create(@Header("Authorization") auth: String, @Path("carId") carId: String, @Path("id") id: String, @Body input: FuelInput): FuelDto
}
