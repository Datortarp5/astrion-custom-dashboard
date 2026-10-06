package com.custom.astrion

import android.app.Application
import com.custom.astrion.alarm.AlarmScheduler
import com.custom.astrion.cards.CardRegistry
import com.custom.astrion.cards.impl.AppleTvRemoteCard
import com.custom.astrion.cards.impl.ButtonGridCard
import com.custom.astrion.cards.impl.CameraCard
import com.custom.astrion.cards.impl.ClimateCard
import com.custom.astrion.cards.impl.ClockWeatherCard
import com.custom.astrion.cards.impl.CoverCard
import com.custom.astrion.cards.impl.FanCard
import com.custom.astrion.cards.impl.LightCard
import com.custom.astrion.cards.impl.MediaPlayerCard
import com.custom.astrion.cards.impl.MonitorCard
import com.custom.astrion.cards.impl.PictureElementsCard
import com.custom.astrion.cards.impl.PlexCard
import com.custom.astrion.cards.impl.RowCard
import com.custom.astrion.cards.impl.SceneGridCard
import com.custom.astrion.cards.impl.SelectCard
import com.custom.astrion.cards.impl.SourceSelectCard
import com.custom.astrion.cards.impl.SpeakerGroupCard
import com.custom.astrion.cards.impl.SwitchCard
import com.custom.astrion.cards.impl.TitleCard
import com.custom.astrion.cards.impl.TvRemoteCard
import com.custom.astrion.cards.impl.VacuumCard
import com.custom.astrion.ha.HaLabels

/**
 * App entry point. Register all card types here once at startup.
 *
 * To add a brand-new native card type:
 *   1. Create a class implementing CardRenderer (see cards/impl/ for examples).
 *   2. Add one line below.
 *   3. Reference it in DashboardConfig with its `type` string.
 *
 * That's the whole extension model — no re-patching anyone's APK, no fixed
 * taxonomy of 11 types.
 */
@Suppress("SpellCheckingInspection")
class AstrionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HaLabels.init(this)
        CardRegistry.register(
            AppleTvRemoteCard(),
            ButtonGridCard(),
            CameraCard(),
            ClimateCard(),
            ClockWeatherCard(),
            CoverCard(),
            SelectCard(),
            FanCard(),
            LightCard(),
            MediaPlayerCard(),
            MonitorCard(),
            PictureElementsCard(),
            PlexCard(),
            RowCard(),
            SceneGridCard(),
            SourceSelectCard(),
            SpeakerGroupCard(),
            SwitchCard(),
            TitleCard(),
            TvRemoteCard(),
            VacuumCard()
            // ← Register your own card types here
        )
        // Every app start re-checks that Android has the next alarm, e.g. after a force-stop dropped it.
        AlarmScheduler.reschedule(this)
    }
}
