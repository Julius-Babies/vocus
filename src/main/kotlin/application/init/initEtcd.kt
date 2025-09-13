package dev.babies.application.init

import dev.babies.application.etcd.EtcdService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object InitEtcd : KoinComponent {
    val etcdService by inject<EtcdService>()
}

suspend fun initEtcd() {
    InitEtcd.etcdService.createIfMissing()
    InitEtcd.etcdService.start()
}