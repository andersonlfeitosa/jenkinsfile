def call(body) {

    properties([
        durabilityHint('PERFORMANCE_OPTIMIZED')
    ])
    
    
    
    node {
        // Clean workspace before doing anything        
        deleteDir()
        
        // Exporting Docker env variables
        env.DOCKER_TLS_VERIFY=1
        env.DOCKER_HOST="tcp://192.168.99.100:2376"
        env.DOCKER_CERT_PATH="/Users/abraao.queiroz/.docker/machine/machines/default"
        env.DOCKER_MACHINE_NAME="default"
        
        echo "Exporting docker enviroment variables"
        //sh "eval $(docker-machine env dev)"

        def VARS = checkout scm

        if (!env.PBRANCH_NAME) {
            env.PBRANCH_NAME = VARS.GIT_BRANCH
        }

        def COMMIT_MESSAGE = sh (script: 'git log -1 --pretty=%B',returnStdout: true).trim()

        if(COMMIT_MESSAGE.startsWith("[maven-release-plugin]")) {
            currentBuild.result = 'FAILURE'
            echo "Commit message starts with maven-release-plugin. Exiting..."
            sh "exit ./build.sh 1" 
        }

        env.PATH = "${tool 'Maven3'}/bin:${env.PATH}"
        env.PATH = "${tool 'jdk1.8'}/bin:${env.PATH}"

        try {
            stage('Checkout') {
                //checkout scm
                echo "branch name = " + PBRANCH_NAME
                sh 'git checkout '+PBRANCH_NAME
                echo "Commit message =  " + COMMIT_MESSAGE
                echo "parameters = " + VERSION + " e " + NEXT_VERSION
            }
            stage('Build') {
                echo "Initializing Build phase"
                sh "mvn clean install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true"
            }
            /*stage('Test') {
                if(!branch_is_feature()) {
                    echo "Initializing test phase"
                    sh "mvn test"
                }
            }
            stage ('Analyse') {
                if(!branch_is_feature()) {
                    echo "Initializing Analyse phase"
                    withSonarQubeEnv('sonar') {
                        sh "mvn sonar:sonar"
                    }
                }
            }
            
            // https://stackoverflow.com/questions/45693418/sonarqube-quality-gate-not-sending-webhook-to-jenkins
            // Please, fix it!
            //sh 'sleep 10'
            
            stage('Quality Gate') {
                 if(!branch_is_feature()) {
                    echo "Initializing Quality Gate phase"
                    timeout(time: 1, unit: 'HOURS') {
                        def qg = waitForQualityGate()
                        if (qg.status != 'OK') {
                            error "Pipeline aborted due to quality gate failure: ${qg.status}"
                        }
                    }
                }
            }*/
            stage('Archive') {
                if(branch_is_master() || branch_is_hotfix()) {
                    echo 'Initializing Archive phase'
                    sh 'mvn deploy -Dmaven.test.skip=true'
                }
            }
            stage ('Release') {
                if(VERSION != NEXT_VERSION) {
                    if(branch_is_master()) {
                        echo 'Initializing Release phase'
                        sh 'git checkout master'
                        sh 'mvn -B release:prepare -DreleaseVersion=${VERSION} -DdevelopmentVersion=${NEXT_VERSION}'
                    } else if(branch_is_hotfix()) {
                        echo 'Initializing Release phase'
                        sh 'git checkout '+PBRANCH_NAME
                        sh 'mvn -B release:prepare -DreleaseVersion=${VERSION} -DdevelopmentVersion=${NEXT_VERSION}'
                    }
                }
            }
                
            stage('Docker') {
                if(VERSION != NEXT_VERSION) {
                    if(branch_is_master() || branch_is_hotfix()) {
                        echo 'Initializing Docker phase'
                        //sh "mvn package docker:build docker:push"
                        docker.withRegistry('https://docker-registry-default.pocose.cabal.com.br', 'docker-openshift-credentials') {
                            def app = docker.build("sippe-jenkins-poc")
                            /* Push the container to the custom Registry */
                            app.push()
                            app.push("${env.BUILD_NUMBER}")
                            app.push("latest")
                        }
                    }
                }
            }
        } catch (err) {
            currentBuild.result = 'FAILED'
            throw err
        }
    }
}


def Boolean branch_is_feature() {
    return test_branch_name("origin/feature/")
}

def Boolean branch_is_master() {
    return test_branch_name("origin/master")
}

def Boolean branch_is_develop() {
    return test_branch_name("origin/develop")
}

def Boolean branch_is_hotfix() {
    return test_branch_name("origin/hotfix/")
}

def Boolean test_branch_name(branch) {
    return env.PBRANCH_NAME.startsWith(branch)
}
