#!/usr/bin/env groovy

@Library('your-shared-library') _

import com.company.jenkins.TestUtils

def call() {
    return new TestUtils(this)
}