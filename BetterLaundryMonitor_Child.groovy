/**
 *  Hubitat Import URL: https://raw.githubusercontent.com/HubitatCommunity/Hubitat-BetterLaundryMonitor/master/BetterLaundryMonitor_Child.groovy
 */

/**
 *  Alert on Power Consumption
 *
 *  Copyright 2015 Kevin Tierney
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

import groovy.time.*

definition(
    name: "Better Laundry Monitor - Power Switch",
    namespace: "tierneykev",
    author: "Kevin Tierney",
    description: "Child: powerMonitor capability, monitor the laundry cycle and alert when it's done.",
    category: "Green Living",
        
    parent: "tierneykev:Better Laundry Monitor",
    
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    )


preferences {
  page name: "mainPage", title: "", install: true, uninstall: true // ,submitOnChange: true      
}

// App Version   ***** with great thanks and acknowlegment to Cobra (CobraVmax) for his original version checking code ********
def setAppVersion(){
     state.version = "1.3.2"
     state.InternalName = "BLMchild"
     state.Type = "Application"
}

def mainPage() {
	dynamicPage(name: "mainPage") {
		display()
		section ("<b>What type of device?</b>") {
			input(name: "deviceType", title: "Type of Device", type: "enum", options: [powerMeter:"Power Meter",accelerationSensor:"Acceleration Sensor"], submitOnChange:true, required:true)
		}
		if (settings.deviceType == "powerMeter") {
			section ("<b>When this device stops drawing power</b>") {
				input "powerMeter", "capability.powerMeter", multiple: false, required: false
			}
			section ("<b>Power Thresholds</b>", hidden: false, hideable: true) {
				input "startThreshold", "decimal", title: "start cycle when power raises above (W)", description: "8", required: false
				input "endThreshold", "decimal", title: "stop cycle when power drops below (W)", description: "4", required: false
				input "delayEnd", "number", title: "stop only after the power has been below the threashold for this many reportings:", description: "2", required: false
			}
		}
		if (settings.deviceType == "accelerationSensor") {
			section("<b>When vibration stops on this device</b>"){
				input "accelerationSensor", "capability.accelerationSensor", multiple: false, required: false
			}
			section("<b>Time Thresholds (in minutes)</b>", hidden: false, hideable: true){
				input "cycleTime", "decimal", title: "Minimum cycle time", required: false, defaultValue: 10
				input "fillTime", "decimal", title: "Time to fill tub", required: false, defaultValue: 5
			}
		}
		if (settings.deviceType != null) {
			section ("<b>Send this message</b>") {
				input "message", "text", title: "Notification message", description: "Laundry is done!", required: true
			}

			section (title: "<b>Using this Notification Method</b>") {
				input "sendPushMessage", "bool", title: "Send a push notification?"
				input "speechOut", "capability.speechSynthesis", title:"Speak Via: (Speech Synthesis)",multiple: true, required: false
				input "player", "capability.musicPlayer", title:"Speak Via: (Music Player -> TTS)",multiple: true, required: false
				input "phone", "phone", title: "Send a text message to:", required: false
			}
			section ("Choose Devices") {
				input "switchList", "capability.switch", title: "Which Switches?", multiple: true, hideWhenEmpty: false, required: false             
			}
		}
			
		section (title: "<b>Other Preferences</b>") {
			label title: "This child app's Name (optional)", required: false
		}
   }
}

def installed() {
	initialize()
	logDebug "Installed with settings: ${settings}"
}

def updated() {
	unsubscribe()
	if (debugOutput) runIn(1800,logsOff)
	initialize()
	logDebug "Updated with settings: ${settings}"
}

def initialize() {
	if (settings.deviceType == "powerMeter") {
		subscribe(powerMeter, "power", handler)
		atomicState.cycleOn = false
		atomicState.powerOffDelay = 0
		startThreshold = startThreshold ?: 8
		endThreshold = endThreshold ?: 4
		delayEnd = delayEnd ?: 2
		state.startThreshold = startThreshold
		state.endThreshold   = endThreshold
		state.delayEnd       = delayEnd
		
		logDebug "Cycle: ${atomicState.cycleOn}, thresholds: ${startThreshold} ${endThreshold} ${delayEnd}"
	} else if (settings.deviceType == "accelerationSensor") {
		subscribe(accelerationSensor, "acceleration.active", accelerationActiveHandler)
		subscribe(accelerationSensor, "acceleration.inactive", accelerationInactiveHandler)
	}
	
	schedule("0 0 14 ? * FRI *", updatecheck)
	version()
	
	if (switchList) {switchList.off()}
}

def logsOff(){
    logDebug "debug logging disabled..."
    app?.updateSetting("debugOutput",[value:"false",type:"bool"])
}

def handler(evt) {
	def latestPower = powerMeter.currentValue("power")

	startThreshold = state.startThreshold
	endThreshold   = state.endThreshold  
	delayEnd       = state.delayEnd

	logDebug "Power: ${latestPower}W, State: ${atomicState.cycleOn}, thresholds: ${startThreshold} ${endThreshold} ${delayEnd}"

	//Added latestpower < 1000 to deal with spikes that triggered false alarms
	if (!atomicState.cycleOn && latestPower && latestPower >= startThreshold) {
		atomicState.cycleOn = true   
		logDebug "Cycle started. State: ${atomicState.cycleOn}"
		if(switchList) { switchList.on() }
	}
	//first time we are below the threashhold, hold and wait for a second.
	else if (atomicState.cycleOn && latestPower < endThreshold && atomicState.powerOffDelay < delayEnd){
		atomicState.powerOffDelay = atomicState.powerOffDelay + 1
		logDebug "We hit delay ${atomicState.powerOffDelay} times"
	}
	//Reset Delay if it only happened once
	else if (atomicState.cycleOn && latestPower >= endThreshold && atomicState.powerOffDelay != 0) {
		logDebug "We hit the delay ${atomicState.powerOffDelay} times but cleared it"
		atomicState.powerOffDelay = 0;
	}
	// If the Machine stops drawing power for X times in a row, the cycle is complete, send notification.
	else if (atomicState.cycleOn && latestPower < endThreshold) {
		send(message)
		atomicState.cycleOn = false
		atomicState.cycleEnd = now()
		atomicState.powerOffDelay = 0
		logDebug "State: ${atomicState.cycleOn}"
		if(switchList) { switchList.off() }
	}
}

def accelerationActiveHandler(evt) {
	logDebug "vibration"
	if (!state.isRunning) {
		logDebug "Arming detector"
		state.isRunning = true
		state.startedAt = now()
		if(switchList) { switchList.on() }
	}
	state.stoppedAt = null
}

def accelerationInactiveHandler(evt) {
	logDebug "no vibration, isRunning: $state.isRunning"
	if (state.isRunning) {
		logDebug "startedAt: ${state.startedAt}, stoppedAt: ${state.stoppedAt}"
		if (!state.stoppedAt) {
			state.stoppedAt = now()
            def delay = Math.floor(fillTime * 60).toInteger()
			runIn(delay, checkRunning, [overwrite: false])
		}
	}
}

def checkRunning() {
	logDebug "checkRunning()"
	if (state.isRunning) {
		def fillTimeMsec = fillTime ? fillTime * 60000 : 300000
		def sensorStates = accelerationSensor.statesSince("acceleration", new Date((now() - fillTimeMsec) as Long))

		if (!sensorStates.find{it.value == "active"}) {
			def cycleTimeMsec = cycleTime ? cycleTime * 60000 : 600000
			def duration = now() - state.startedAt
			if (duration - fillTimeMsec > cycleTimeMsec) {
				if(switchList) { switchList.off() }
				logDebug "Sending notification"
				send(message)
			} else {
				logDebug "Not sending notification because machine wasn't running long enough $duration versus $cycleTimeMsec msec"
			}
			state.isRunning = false
			logDebug "Disarming detector"
		} else {
			logDebug "skipping notification because vibration detected again"
		}
	}
	else {
		logDebug "machine no longer running"
	}
}

private send(msg) {
	if (sendPushMessage) {
		sendPush(msg)
	}

	if (phone) {
		sendSms(phone, msg)
	}
	
	if (speechOut) {
		speakMessage(message)
	}
	if (player) {
		musicPlayerTTS(message)
	}

	logDebug msg
}

private speakMessage(msg) {
	speechOut.speak(msg)
}

private musicPlayerTTS(msg) {
	player.playText(msg)
}

private hideOptionsSection() {
	(phone || switches || hues || color || lightLevel) ? false : true
}

// Check Version   ***** with great thanks and acknowlegment to Cobra (CobraVmax) for his original version checking code ********
def version(){
	updatecheck()
	if (state.Type == "Application") { schedule("0 0 14 ? * FRI *", updatecheck) }
	if (state.Type == "Driver") { schedule("0 45 16 ? * MON *", updatecheck) }
}

def display(){
	updatecheck()
	section{
		paragraph "Version Status: $state.Status"
		paragraph "Current Version: $state.version -  $state.Copyright"
	}
}

def updatecheck(){
	setAppVersion()
	def paramsUD = [uri: "https://hubitatcommunity.github.io/Hubitat-BetterLaundryMonitor/versions.json"]
	try {
		httpGet(paramsUD) { respUD ->
			//  log.info " Version Checking - Response Data: ${respUD.data}"
			def copyNow = (respUD.data.copyright)
			state.Copyright = copyNow
			def newver = (respUD.data.versions.(state.Type).(state.InternalName))
			def updatecheckVer = (respUD.data.versions.(state.Type).(state.InternalName).replace(".", ""))
			def updatecheckOld = state.version.replace(".", "")
			if (updatecheckOld < updatecheckVer){
				state.Status = "<b>** New Version Available (Version: $newver) **</b>"
				log.warn "** There is a newer version of this $state.Type available  (Version: $newver) **"
			} else { 
				state.Status = "Current"
				log.info "$state.Type is the current version"
			}
		}
	} catch (e) {
		log.error "Something went wrong: $e"
	}
}

private logDebug(msg) {
	parent.logDebug(msg)
}
