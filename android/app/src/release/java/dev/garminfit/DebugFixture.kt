package dev.garminfit

import okhttp3.Interceptor

/** Release builds have no fixtures; the debug build's DebugFixture fakes plan states for QA. */
object DebugFixture : Interceptor {
    override fun intercept(chain: Interceptor.Chain) = chain.proceed(chain.request())
}
