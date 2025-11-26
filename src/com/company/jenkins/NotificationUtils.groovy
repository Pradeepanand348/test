package com.company.jenkins

/**
 * Notification Utilities
 * 
 * Handles all notification and communication
 */
class NotificationUtils implements Serializable {
    
    private def script
    
    NotificationUtils(script) {
        this.script = script
    }
    
    /**
     * Send build notification based on result
     */
    def sendBuildNotification(Map config) {
        def result = config.result ?: 'SUCCESS'
        
        switch (result) {
            case 'SUCCESS':
                this.sendSuccessNotification(config)
                break
            case 'FAILURE':
                this.sendFailureNotification(config)
                break
            case 'UNSTABLE':
                this.sendUnstableNotification(config)
                break
            case 'ABORTED':
                this.sendAbortedNotification(config)
                break
            default:
                this.sendGenericNotification(config)
        }
    }
    
    /**
     * Send success notification
     */
    def sendSuccessNotification(Map config) {
        script.echo "📧 Sending success notification"
        
        def subject = "✅ Build Successful - ${config.jobName} #${config.buildNumber}"
        def body = """
🎉 All pipeline stages completed successfully!

📋 Job Details:
• Job: ${config.jobName}
• Build Number: ${config.buildNumber}
• Build URL: ${config.buildUrl}
• Git Branch: ${config.gitBranch ?: 'Not specified'}
• Build Timestamp: ${config.buildTimestamp ?: 'Not available'}

✅ Completed Stages:
• SCMS Core Build - SUCCESS
• Bannow Builder - SUCCESS  
• AMI Builder - SUCCESS
• Test Execution - SUCCESS

📦 Artifacts:
• RPM packages built and deployed
• Test results archived and analyzed
• Build artifacts available for download

🚀 All components have been successfully built, tested, and deployed.

Build Details: ${config.buildUrl}
"""
        
        this.sendEmail([
            subject: subject,
            body: body,
            recipients: config.recipients
        ])
    }
    
    /**
     * Send failure notification
     */
    def sendFailureNotification(Map config) {
        script.echo "📧 Sending failure notification"
        
        def stageName = config.stage ?: 'Unknown Stage'
        def subject = "❌ Build Failed - ${config.jobName} #${config.buildNumber} - ${stageName}"
        def body = """
❌ Pipeline execution failed during ${stageName}

📋 Job Details:
• Job: ${config.jobName}
• Build Number: ${config.buildNumber}
• Build URL: ${config.buildUrl}
• Git Branch: ${config.gitBranch ?: 'Not specified'}
• Failed Stage: ${stageName}

❌ Error Details:
${config.error ?: 'No specific error message available'}

⛔ Impact:
• Subsequent builds have been cancelled
• No artifacts will be deployed
• Manual intervention required

🔧 Next Steps:
1. Check the console output: ${config.buildUrl}console
2. Review the error logs and fix the issues
3. Re-run the pipeline after fixes

Build Details: ${config.buildUrl}
"""
        
        this.sendEmail([
            subject: subject,
            body: body,
            recipients: config.recipients,
            priority: 'high'
        ])
    }
    
    /**
     * Send unstable notification
     */
    def sendUnstableNotification(Map config) {
        script.echo "📧 Sending unstable notification"
        
        def subject = "⚠️ Build Unstable - ${config.jobName} #${config.buildNumber}"
        def body = """
⚠️ Build completed but marked as unstable

📋 Job Details:
• Job: ${config.jobName}
• Build Number: ${config.buildNumber}
• Build URL: ${config.buildUrl}
• Git Branch: ${config.gitBranch ?: 'Not specified'}

⚠️ Issues Found:
• Some tests may have been skipped
• Test results indicate potential issues
• Build artifacts created but may have quality concerns

🔍 Investigation Required:
• Review test results for skipped/failed tests
• Check build logs for warnings
• Verify artifact quality before deployment

Build Details: ${config.buildUrl}
"""
        
        this.sendEmail([
            subject: subject,
            body: body,
            recipients: config.recipients
        ])
    }
    
    /**
     * Send aborted notification
     */
    def sendAbortedNotification(Map config) {
        script.echo "📧 Sending aborted notification"
        
        def subject = "⏹️ Build Aborted - ${config.jobName} #${config.buildNumber}"
        def body = """
⏹️ Build was aborted before completion

📋 Job Details:
• Job: ${config.jobName}
• Build Number: ${config.buildNumber}
• Build URL: ${config.buildUrl}
• Git Branch: ${config.gitBranch ?: 'Not specified'}

⏹️ Possible Reasons:
• Manual cancellation by user
• Timeout exceeded
• System resource constraints
• Dependencies unavailable

🔧 Next Steps:
• Check if manual cancellation was intended
• Review system resources and dependencies
• Re-run pipeline if cancellation was unintended

Build Details: ${config.buildUrl}
"""
        
        this.sendEmail([
            subject: subject,
            body: body,
            recipients: config.recipients
        ])
    }
    
    /**
     * Send generic notification
     */
    def sendGenericNotification(Map config) {
        script.echo "📧 Sending generic notification"
        
        def result = config.result ?: 'UNKNOWN'
        def subject = "📊 Build ${result} - ${config.jobName} #${config.buildNumber}"
        def body = """
📊 Build completed with result: ${result}

📋 Job Details:
• Job: ${config.jobName}
• Build Number: ${config.buildNumber}
• Build URL: ${config.buildUrl}
• Git Branch: ${config.gitBranch ?: 'Not specified'}
• Result: ${result}

Please check the build details for more information.

Build Details: ${config.buildUrl}
"""
        
        this.sendEmail([
            subject: subject,
            body: body,
            recipients: config.recipients
        ])
    }
    
    /**
     * Send email using Jenkins emailext
     */
    private def sendEmail(Map config) {
        def priority = config.priority ?: 'normal'
        def attachLogs = config.attachLogs ?: false
        
        try {
            script.emailext([
                subject: config.subject,
                body: config.body,
                to: config.recipients,
                mimeType: 'text/plain',
                attachLog: attachLogs,
                compressLog: true,
                recipientProviders: [
                    [$class: 'CulpritsRecipientProvider'],
                    [$class: 'DevelopersRecipientProvider'],
                    [$class: 'RequesterRecipientProvider']
                ]
            ])
            script.echo "✅ Email notification sent successfully"
        } catch (Exception e) {
            script.echo "❌ Failed to send email notification: ${e.getMessage()}"
        }
    }
    
    /**
     * Send Slack notification (if Slack plugin is available)
     */
    def sendSlackNotification(Map config) {
        try {
            def color = this.getSlackColor(config.result)
            def message = this.buildSlackMessage(config)
            
            script.slackSend([
                channel: config.channel ?: '#builds',
                color: color,
                message: message,
                teamDomain: config.teamDomain,
                token: config.token
            ])
            script.echo "✅ Slack notification sent successfully"
        } catch (Exception e) {
            script.echo "⚠️ Slack notification not available or failed: ${e.getMessage()}"
        }
    }
    
    /**
     * Get Slack color based on build result
     */
    private def getSlackColor(String result) {
        switch (result) {
            case 'SUCCESS':
                return 'good'
            case 'FAILURE':
                return 'danger'
            case 'UNSTABLE':
                return 'warning'
            case 'ABORTED':
                return '#808080'
            default:
                return '#808080'
        }
    }
    
    /**
     * Build Slack message
     */
    private def buildSlackMessage(Map config) {
        def emoji = this.getResultEmoji(config.result)
        return "${emoji} ${config.jobName} #${config.buildNumber} - ${config.result}\nBranch: ${config.gitBranch}\n<${config.buildUrl}|View Build>"
    }
    
    /**
     * Get emoji for build result
     */
    private def getResultEmoji(String result) {
        switch (result) {
            case 'SUCCESS':
                return '✅'
            case 'FAILURE':
                return '❌'
            case 'UNSTABLE':
                return '⚠️'
            case 'ABORTED':
                return '⏹️'
            default:
                return '❓'
        }
    }
}