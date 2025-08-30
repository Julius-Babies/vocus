package dev.babies.application.os

import kotlinx.coroutines.runBlocking
import javax.swing.JFrame
import javax.swing.JPasswordField

class SudoManager {
    private var password: String? = null

    fun get(): String {
        if (password != null) return password!!

        if (System.console() == null || !System.console().isTerminal) {
            val frame = JFrame("Sudo Password Required")
            frame.isVisible = true

            frame.setSize(300, 100)
            frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            frame.setLocationRelativeTo(null)

            val passwordField = JPasswordField()
            passwordField.echoChar = '*'
            passwordField.addActionListener {
                password = String(passwordField.password)
                frame.dispose()
            }
            frame.add(passwordField)

            runBlocking {
                frame.isVisible = true
                while (password == null) {
                    Thread.sleep(100)
                }
            }
        } else {
            password = System.console().readPassword("Sudo Password: ").joinToString("")
        }

        return password!!
    }
}