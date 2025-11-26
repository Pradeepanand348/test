package com.company.jenkins

/**
 * Git Utilities
 * 
 * Handles all Git-related operations
 */
class GitUtils implements Serializable {
    
    private def script
    
    GitUtils(script) {
        this.script = script
    }
    
    /**
     * Checkout repository with given parameters
     */
    def checkoutRepository(Map config) {
        script.echo "📥 Checking out repository: ${config.url}"
        script.echo "🌿 Branch: ${config.branch}"
        
        def checkoutConfig = [
            $class: 'GitSCM',
            branches: [[name: "*/${config.branch}"]],
            userRemoteConfigs: [
                [credentialsId: config.credentialsId, url: config.url]
            ]
        ]
        
        // Add browser configuration if provided
        if (config.browser) {
            checkoutConfig.browser = config.browser
        }
        
        script.checkout(checkoutConfig)
    }
    
    /**
     * Get short commit hash
     */
    def getShortCommitHash() {
        return script.sh(
            script: "git rev-parse --short HEAD",
            returnStdout: true
        ).trim()
    }
    
    /**
     * Get full commit hash
     */
    def getFullCommitHash() {
        return script.sh(
            script: "git rev-parse HEAD",
            returnStdout: true
        ).trim()
    }
    
    /**
     * Get commit message
     */
    def getCommitMessage() {
        return script.sh(
            script: "git log -1 --pretty=format:'%s'",
            returnStdout: true
        ).trim()
    }
    
    /**
     * Get commit author
     */
    def getCommitAuthor() {
        return script.sh(
            script: "git log -1 --pretty=format:'%an'",
            returnStdout: true
        ).trim()
    }
    
    /**
     * Check if branch exists
     */
    def branchExists(String branch) {
        try {
            script.sh("git show-ref --verify --quiet refs/heads/${branch}")
            return true
        } catch (Exception e) {
            return false
        }
    }
    
    /**
     * Get list of changed files
     */
    def getChangedFiles(String baseBranch = 'origin/master') {
        return script.sh(
            script: "git diff --name-only ${baseBranch}",
            returnStdout: true
        ).trim().split('\n').findAll { it.trim() }
    }
}