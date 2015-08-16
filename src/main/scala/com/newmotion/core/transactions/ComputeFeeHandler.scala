package com.newmotion.core.transactions

import com.newmotion.models.Fee
import com.newmotion.server.RedisStore
import com.newmotion.server.http.Responses._
import com.newmotion.service.tracing.Tracing
import com.newmotion.util.DateSupport
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.redis.util.CBToString
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http.HttpResponseStatus

import scala.util.{Failure, Success, Try}

class ComputeFeeHandler extends Service[Request, Response] with Tracing with RedisStore {
  override def apply(request: Request): Future[Response] = {
    Try(Fee(request)) match {
      case Success(t) =>
        // todo remove this when sscan is implemented by finagle-redis
        isValid(t.activeStarting) map {
          case true =>
            val key = s"${t.activeStarting},${t.startFee},${t.hourlyFee},${t.feePerKWh}"
            addSet("tariffs", key)
            respond("", HttpResponseStatus.CREATED)
          case false =>
            respond("", HttpResponseStatus.BAD_REQUEST)
        }
      case Failure(f) => Future(respond("Errors!", HttpResponseStatus.BAD_REQUEST))
    }
  }

  def extractFirstField(v: String) = v.substring(0, v.indexOf(","))

  // todo remove this when sscan is implemented by finagle-redis
  def isValid(value: String): Future[Boolean] = {
    val ds = new DateSupport
    val date = ds.parse(value)
    getAllMembers("tariffs") map {
      resp =>
        val total = resp.size
        val validDates = resp.takeWhile(r => ds.parse(extractFirstField(CBToString(r))).isBefore(date)).size
        if (total > validDates) false else true
    }
  }
}
