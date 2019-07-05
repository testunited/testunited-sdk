pipeline {
    agent any 
    stages {
        stage('Build') { 
            steps {
                sh "gradle build -x test"
            }
        }
        stage('Dev Test') { 
            steps {
                sh "gradle test"
            }
        }
        stage('Package') { 
            steps {
                sh "gradle jar"
            }
        }
        stage('Publish') { 
            steps {
                sh "gradle publish publishToMavenLocal"
            }
        }
    }
}