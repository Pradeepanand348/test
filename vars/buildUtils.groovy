#!/usr/bin/env groovy

@Library('your-shared-library') _

import com.company.jenkins.BuildUtils

def call() {
    return new BuildUtils(this)
}