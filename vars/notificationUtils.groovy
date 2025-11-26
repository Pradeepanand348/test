#!/usr/bin/env groovy

@Library('your-shared-library') _

import com.company.jenkins.NotificationUtils

def call() {
    return new NotificationUtils(this)
}