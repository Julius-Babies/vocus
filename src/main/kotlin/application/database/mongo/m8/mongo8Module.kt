package dev.babies.application.database.mongo.m8

import org.koin.dsl.module

val mongo8Module = module {
    single { Mongo8Database() }
}