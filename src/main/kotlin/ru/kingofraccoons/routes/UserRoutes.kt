package ru.kingofraccoons.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.kingofraccoons.dao.RecordDAO
import ru.kingofraccoons.dao.UserDAO
import ru.kingofraccoons.models.ErrorResponse
import ru.kingofraccoons.models.UserInfo

fun Route.userRoutes(
    userDAO: UserDAO,
    recordDAO: RecordDAO
) {
    authenticate("auth-jwt") {
        get("/recordInfo") {
            val userId = call.requireUserId() ?: return@get

            val user = userDAO.findById(userId)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("User not found", HttpStatusCode.NotFound.value)
                )

            val countRecords = recordDAO.countByUserId(userId).toInt()
            val totalSeconds = recordDAO.sumDurationByUserId(userId)
            val countMinutes = (totalSeconds / 60).toInt()

            call.respond(
                HttpStatusCode.OK,
                UserInfo(
                    id = user.id,
                    email = user.email,
                    fullName = user.fullName,
                    countRecords = countRecords,
                    countMinutes = countMinutes
                )
            )
        }
    }
}
