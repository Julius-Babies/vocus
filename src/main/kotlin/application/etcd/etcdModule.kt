package dev.babies.application.etcd

import org.koin.dsl.module

val etcdModule = module {
    single { EtcdService() }
}