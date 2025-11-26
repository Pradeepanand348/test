#!/usr/bin/env groovy

@Library('your-shared-library') _

import com.company.jenkins.ArtifactUtils

def call() {
    return new ArtifactUtils(this)
}