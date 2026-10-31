package de.max.gedaechtnis.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.max.gedaechtnis.data.AppDatabase
import de.max.gedaechtnis.data.Clock
import de.max.gedaechtnis.data.SystemClock
import de.max.gedaechtnis.kim.engine.Balancing
import de.max.gedaechtnis.kim.engine.Catalog
import de.max.gedaechtnis.kim.engine.Difficulty
import de.max.gedaechtnis.kim.engine.Evaluator
import de.max.gedaechtnis.kim.engine.AdaptiveRule
import de.max.gedaechtnis.kim.engine.Progression
import de.max.gedaechtnis.kim.engine.RoundGenerator
import de.max.gedaechtnis.kim.engine.Scorer
import de.max.gedaechtnis.leitner.engine.Scheduler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private fun Context.asset(name: String) = assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Provides @Singleton fun balancing(@ApplicationContext c: Context): Balancing = Balancing.parse(c.asset("balancing.json"))
    @Provides @Singleton fun catalog(@ApplicationContext c: Context): Catalog = Catalog.parse(c.asset("catalog.json"))
    @Provides @Singleton fun difficulty(b: Balancing) = Difficulty(b)
    @Provides @Singleton fun generator(c: Catalog, b: Balancing) = RoundGenerator(c, b)
    @Provides @Singleton fun evaluator(c: Catalog) = Evaluator(c)
    @Provides @Singleton fun scorer(b: Balancing) = Scorer(b.scoring)
    @Provides @Singleton fun progression(b: Balancing) = Progression(b)
    @Provides @Singleton fun adaptive(b: Balancing) = AdaptiveRule(b.adaptive)
    @Provides @Singleton fun scheduler(b: Balancing) = Scheduler(b.leitner)
    @Provides @Singleton fun clock(impl: SystemClock): Clock = impl

    @Provides @Singleton
    fun database(@ApplicationContext c: Context): AppDatabase =
        Room.databaseBuilder(c, AppDatabase::class.java, "gedaechtnis.db").build()
}
