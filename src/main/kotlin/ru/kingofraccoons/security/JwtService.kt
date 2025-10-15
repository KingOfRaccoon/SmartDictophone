package ru.kingofraccoons.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import java.util.*

class JwtService(config: Application) {
    private val jwtConfig = config.environment.config.config("jwt")
    
    val secret = jwtConfig.property("secret").getString()
    val issuer = jwtConfig.property("issuer").getString()
    val audience = jwtConfig.property("audience").getString()
    val realm = jwtConfig.property("realm").getString()
    private val accessTokenExpiration = jwtConfig.property("accessTokenExpiration").getString().toLong()
    private val refreshTokenExpiration = jwtConfig.property("refreshTokenExpiration").getString().toLong()

    fun generateAccessToken(userId: Long, email: String): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("type", "access")
            .withExpiresAt(Date(System.currentTimeMillis() + accessTokenExpiration))
            .sign(Algorithm.HMAC256(secret))
    }

    fun generateRefreshToken(userId: Long, email: String): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("type", "refresh")
            .withExpiresAt(Date(System.currentTimeMillis() + refreshTokenExpiration))
            .sign(Algorithm.HMAC256(secret))
    }

    fun verifyToken(token: String): Long? {
        return try {
            val verifier = JWT.require(Algorithm.HMAC256(secret))
                .withAudience(audience)
                .withIssuer(issuer)
                .build()
            
            val decodedJWT = verifier.verify(token)
            decodedJWT.getClaim("userId").asLong()
        } catch (e: Exception) {
            null
        }
    }
}

object PasswordHasher {
    fun hashPassword(password: String): String {
        return org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt())
    }

    fun checkPassword(password: String, hash: String): Boolean {
        return org.mindrot.jbcrypt.BCrypt.checkpw(password, hash)
    }
}
