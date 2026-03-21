/**
 *  Hubitat Import URL: https://raw.githubusercontent.com/HubitatCommunity/Hubitat-BetterLaundryMonitor/master/BetterLaundryMonitor_Child.groovy
 */

/**
 *  Alert on Power Consumption
 *
 *  Copyright 2015 Kevin Tierney, C Steele
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
 *
 *
 * csteele: v1.7.0	Add Calibration option to Thresholds
 * csteele: v1.6.0	Add Arbitrary Device / Attribute
 *                	 Normalized log.debug. 
 * csteele: v1.5.0	Add Contact sensor child
 *                	 Remove UpdateCheck, rely on HPM to check for a new version.
 *
 */

	public static String version()      {  return "v1.7.0"  }


import groovy.time.*

definition(
	name: "Better Laundry Monitor - Power Switch",
	namespace: "tierneykev",
	author: "Kevin Tierney, ChrisUthe, CSteele",
	description: "Child: powerMonitor capability, monitor the laundry cycle and alert when it's done.",
	category: "Green Living",
	importUrl: "https://raw.githubusercontent.com/HubitatCommunity/Hubitat-BetterLaundryMonitor/master/BetterLaundryMonitor_Child.groovy",
	parent: "tierneykev:Better Laundry Monitor",
	
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: ""
)


preferences {
	page (name: "mainPage")
	page (name: "sensorPage")
	page (name: "thresholdPage")
	page (name: "informPage")
}
//<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>


def mainPage() {
	dynamicPage(name: "mainPage", install: true, uninstall: true) {
		updateMyLabel()
		section("<h2>${app.label ?: app.name}</h2>"){
			if (!atomicState.isPaused) {
				input(name: "pauseButton", type: "button", title: "Pause", backgroundColor: "Green", textColor: "white", submitOnChange: true)
			} else {
				input(name: "resumeButton", type: "button", title: "Resume", backgroundColor: "Crimson", textColor: "white", submitOnChange: true)
			}
		}
		section("-= <b>Main Menu</b> =-") 
		{
			input (name: "deviceType", title: "Type of Device", type: "enum", options: [powerMeter:"Power Meter", accelerationSensor:"Sequence Vibration Sensor", accelSensor:"Timed Vibration Sensor", contactSensor:"Contact Sensor", usersSensor:"Arbitrary Sensor"], required:true, submitOnChange:true)
		}

		if (deviceType) {
			section
			{
				href "sensorPage", title: "Sensors", description: "Sensors to be monitored", state: selectOk?.sensorPage ? "complete" : null
				href "thresholdPage", title: "Thresholds", description: "Thresholds to be monitored", state: selectOk?.thresholdPage ? "complete" : null
				href "informPage", title: "Inform", description: "Who and what to Inform", state: selectOk?.informPage ? "complete" : null
			}
		}
		
		section (title: "<b>Reset/End Cycle</b>") {
			input(name: "resetButton", type: "button", title: "Reset", backgroundColor: "Crimson", textColor: "white", submitOnChange: true)
		}
		section (title: "<b>Name/Rename</b>") {
			label title: "This child app's Name (optional)", required: false, submitOnChange: true
			if (!app.label) {
				app.updateLabel(app.name)
				atomicState.appDisplayName = app.name
			}
			if (app.label.contains('<span ')) {
				if (atomicState?.appDisplayName != null) {
					app.updateLabel(atomicState.appDisplayName)
				} else {
					String myLabel = app.label.substring(0, app.label.indexOf('<span '))
					atomicState.appDisplayName = myLabel
					app.updateLabel(myLabel)
				}				
			}
		}
		reSubscribe()
		display()
	}
}


def sensorPage() {
	dynamicPage(name: "sensorPage") {
		if (deviceType == "powerMeter") {
			section ("<b>When this device starts/stops drawing power</b>") {
				input "pwrMeter", "capability.powerMeter", title: "Power Meter" , multiple: false, required: false, defaultValue: null
			}
		}
		if (deviceType == "accelerationSensor" || deviceType == "accelSensor") {
			section("<b>When vibration stops on this device</b>") {
				input "accelSensor", "capability.accelerationSensor", title: "Acceleration Sensor" , multiple: false, required: false, defaultValue: null
			}
		}
		if (deviceType == "contactSensor") {
			section("<b>When Contact stops on this device</b>") {
				input "contactSensor", "capability.contactSensor", title: "Contact Sensor" , multiple: false, required: false, defaultValue: null
			}
		}
		if (deviceType == "usersSensor") {
			section("<b>Pick an Arbitrary Sensor</b>") {
				input "userSensor", "capability.*", title: "Arbitrary Sensor" , multiple: false, required: false, defaultValue: null, submitOnChange: true
			}
		}
		if (deviceType == "usersSensor" && userSensor) {
            section("<b>Pick a Device Attribute</b>") { ///
				tags = userSensor.supportedAttributes.collect{''+ it +''} 
				input "userAttrib", "enum",  options: tags, title: "Arbitrary Attribute" , multiple: false, required: false, defaultValue: null, submitOnChange: true
			}
		}
		if (deviceType == "usersSensor" && userSensor && tags) {
            section("<b>Pick an Attribute Type</b>") {
            		tagType = ["powerMeter": "Power", "accelerationSensor": "Vibration Sensor", "contactSensor": "Contact Sensor"]
				input "userAttribType", "enum",  options: tagType, title: "Arbitrary Attribute" , multiple: false, required: false, defaultValue: null
			}
		}
	}
}


def thresholdPage() {
	dynamicPage(name: "thresholdPage") {
		if (deviceType == "powerMeter" || userAttribType == "powerMeter") {
			section("<b>Calibration (Optional)</b>", hidden: false, hideable: true) {
				input "calibrationEnabled", "bool", title: "Enable calibration mode", defaultValue: false, required: false, submitOnChange: true
				if (calibrationEnabled) {
					input "calibrationAutoApply", "bool", title: "Auto-apply recommended thresholds", defaultValue: false, required: false
					if (state?.calibration?.status) {
						paragraph "Calibration status: ${state.calibration.status}"
					}
					if (!calibrationAutoApply) {
						input "applyCalButton", "button", title: "Apply Recommended Thresholds", backgroundColor: "Green", textColor: "white", submitOnChange: true
					}
					input "resetCalButton", "button", title: "Reset Learned Stats", backgroundColor: "Crimson", textColor: "white", submitOnChange: true
					input "powerLogText", "textarea", title: "Optional: Paste Power Logs (for offline idle/active learn)", required: false, submitOnChange: true
					input "parseLogsButton", "button", title: "Parse Logs + Recommend", backgroundColor: "Gray", textColor: "white", submitOnChange: true
					if (state?.calibration?.recommended) {
						def rec = state.calibration.recommended
						paragraph "Recommended Power Values: <b>Start</b> = ${rec.startThreshold}W, <b>Stop</b> = ${rec.endThreshold}W, <b>Ignore</b> = ${rec.ignoreThreshold}W"
						paragraph "Recommended Delay Values: <b>delayEndPwr</b> = ${rec.delayEndPwr ?: 'n/a'} reports, <b>delayEndDelay</b> = ${rec.delayEndDelay ?: 'n/a'} minutes"
					}
					if (state?.calibration?.status && isCalibrationSparse()) {
						paragraph "<b>Note:</b> Not enough samples yet for reliable recommendations."
					}
				}
			}
		}
		if (deviceType == "accelerationSensor" || userAttribType == "accelerationSensor") {
			section("<b>Vibration Thresholds</b>", hidden: false, hideable: true) {
				input "delayEndAcc", "number", title: "Stop after no vibration for this many sequential reportings:", defaultValue: "2", required: false
				input "cycleMax", "number", title: "Optional: Maximum cycle time (acts as a deadman timer.)", required: false
			}
		}
		if (deviceType == "powerMeter" || userAttribType == "powerMeter") {
			section ("<b>Power Thresholds</b>", hidden: false, hideable: true) {
                paragraph "<b>Start of Cycle logic:</b> (to control when start-of-cycle is detected.)"
                input "startTimeThreshold", "number", title: "Optional: Time (in minutes) to wait before counting power threshold.  Great for pre-wash soaks.", required: false
				input "startThreshold", "decimal", title: "Start cycle when power (W) raises above (<b>Start</b>)", defaultValue: "8", required: false
                paragraph "<br><b>End of Cycle logic:</b> (to control when end-of-cycle is detected.)"
				input "minEndDetectMins", "number", title: "Optional: Minimum minutes after start before end detection begins", required: false
                input "endThreshold", "decimal", title: "Stop cycle when power (W) drops below (<b>Stop</b>)", defaultValue: "4", required: false
				input "delayEndPwr", "number", title: "Stop after power has been below the threshold for this many sequential reportings (<b>delayEndPwr</b>):", defaultValue: "2", required: false
				input "delayEndDelay", "number", title: "Stop after power has been below the threshold for this many continuous minutes (<b>delayEndDelay</b>):", defaultValue: "0", required: false
	            paragraph "<br><b>Tune Cycle logic:</b> (to ignore spikes and cap maximum runtime.)"				
                input "ignoreThreshold", "decimal", title: "Optional: Ignore extraneous power (W) readings above (<b>Ignore</b>)", defaultValue: "1500", required: false
				input "cycleMax", "number", title: "Optional: Maximum cycle time (acts as a deadman timer.)", required: false
                paragraph "<br><b>Wrinkle Cycle logic:</b> (to add a second notification, for dryers.)"				
                input "wrinkleCycleCount", "number", title: "Optional: Wrinkle cycle count (send extra notification after N wrinkle cycles)", required: false
				input "wrinkleWindowMins", "number", title: "Optional: Wrinkle monitoring window (minutes)", defaultValue: "120", required: false
				input "wrinkleMessage", "text", title: "Optional: Wrinkle cycle notification message", required: false
			}
		}
		if (deviceType == "accelSensor" || userAttribType == "accelSensor") {
			section("<b>Time Thresholds (in minutes)</b>", hidden: false, hideable: true) {
				input "fillTime", "decimal", title: "Time to fill tub (0 for Dryer)", required: false, defaultValue: 5
				input "cycleTime", "decimal", title: "Minimum cycle time", required: false, defaultValue: 10
				input "cycleMax", "number", title: "Optional: Maximum cycle time (acts as a deadman timer.)", required: false
			}
		}
		if (deviceType == "contactSensor" || userAttribType == "contactSensor") {
			section ("<b>Contact Thresholds</b>", hidden: false, hideable: true) {
				input "contCycleCount", "number", title: "Stop after this many cycles:", defaultValue: "2", required: false
				input "cycleMax", "number", title: "Optional: Maximum cycle time (acts as a deadman timer.)", required: false
			}
		}
	}
}


def informPage() {
	dynamicPage(name: "informPage") {
		section ("<b>Send this message</b>", hidden: false, hideable: true) {
			input "messageStart", "text", title: "Notification message Start (optional)", description: "Laundry is started!", required: false
			input "message", "text", title: "Notification message End", description: "Laundry is done!", required: true
		}
		section (title: "<b>Using this Notification Method</b>", hidden: false, hideable: true) {
			input "textNotification", "capability.notification", title: "Send Via: (Notification)", multiple: true, required: false
			input "speechOut", "capability.speechSynthesis", title:"Speak Via: (Speech Synthesis)", multiple: true, required: false
			input "player", "capability.musicPlayer", title:"Speak Via: (Music Player -> TTS)", multiple: true, required: false
			input "blockIt", "capability.switch", title: "Switch to Block Speak if ON", multiple: false, required: false
		}
		section ("<b>Choose Additional Devices</b>") {
		  	input "switchList", "capability.switch", title: "Which Switches?", description: "Switches to follow the active state", multiple: true, hideWhenEmpty: false, required: false             
		}
	}
}


def getSelectOk()
{
	def status =
	[
		sensorPage: pwrMeter ?: accelSensor ?: contactSensor ?: userSensor,
		thresholdPage: cycleTime ?: fillTime ?: startThreshold ?: endThreshold ?: delayEndAcc ?: delayEndPwr ?: contCycleCount,
		informPage: messageStart?.size() ?: message?.size()
	]
	status << [all: status.sensorPage ?: status.thresholdPage ?: status.informPage]
}


def powerHandler(evt) {
	def latestPower = pwrMeter.currentValue("power")
	def delayEPloop = delayEndPwr-1 
	calibrationSample(latestPower)
	updateCalTiming(latestPower)
	checkWrinkleExpiry()

	if (debugOutput) log.debug "Power: ${latestPower}W, State: ${atomicState.cycleOn}, thresholds: ${startThreshold} ${endThreshold} ${delayEndPwr} ${delayEndDelay} optional: ${ignoreThreshold} ${startTimeThreshold} ${cycleMax}"
	
	if (latestPower > endThreshold && atomicState.cycleEnding) {
		atomicState.cycleEnd = -1
		atomicState.cycleEnding = false
		if (debugOutput) log.debug "Resetting end timer"
	}
	else if (!atomicState.cycleOn && (latestPower >= startThreshold) && (latestPower < ignoreThreshold)) { // latestpower < 1000: eliminate spikes that trigger false alarms
		if (wrinkleEnabled() && state.wrinkleActive) {
			state.wrinkleRunning = true
			atomicState.powerOffDelay = 0
			if (debugOutput) log.debug "Wrinkle cycle restart detected."
			return
		}
		send(messageStart)
		atomicState.cycleOn = true
		atomicState.cycleEnding = false
		atomicState.cycleStart = now()
		updateMyLabel()
		if (descTextEnable) log.info "Cycle started."
		if(switchList) { switchList*.on() }
		if (cycleMax) { // start the deadman timer
		    def delay = Math.floor(cycleMax * 60).toInteger()
		    runIn(delay, checkCycleMax)
		}
	}
	//If Start Time Threshold was set, check if we have waited that number of minutes before counting the power thresholds
	else if (startTimeThreshold && delayPowerThreshold()) {
        //do nothing
		if (latestPower < endThreshold) {
			atomicState.cycleOn = false
			atomicState.powerOffDelay = 0
			state.remove("startedAt")
			if (descTextEnable) log.info "Dropped below threshold before start time threshold, cancelling."
		}
	}
	// Delay end detection until minimum runtime has elapsed (e.g., soak phases)
	else if (atomicState.cycleOn && !state.wrinkleRunning && !canCheckEndNow() && latestPower < endThreshold) {
		if (debugOutput) log.debug "End detection delayed (${minEndDetectMins} min minimum runtime not reached)."
		return
	}
	//first time we are below the threshold, hold and wait for X more.
	else if ((atomicState.cycleOn || state.wrinkleRunning) && latestPower < endThreshold && atomicState.powerOffDelay < delayEPloop){
		atomicState.powerOffDelay++
		if (debugOutput) log.debug "We hit Power Delay ${atomicState.powerOffDelay} times"
	}
	//Reset Delay if it only happened once
	else if ((atomicState.cycleOn || state.wrinkleRunning) && latestPower >= endThreshold && atomicState.powerOffDelay != 0) {
		if (debugOutput) log.debug "We hit the Power Delay ${atomicState.powerOffDelay} times but cleared it"
		atomicState.powerOffDelay = 0
	}
	// If the Machine stops drawing power for X times in a row, the cycle is complete, send notification
	// or schedule future check if cycleEnd isn't set already
	else if ((atomicState.cycleOn || state.wrinkleRunning) && (latestPower < endThreshold) && (atomicState.cycleEnding != true)) {
		// cycleDone already scheduled if cycleEnd is set already
		atomicState.cycleEnding = true
		atomicState.cycleEnd = now()
		if (delayEndDelay > 0) {
			if (debugOutput) log.debug "Ending duration is set, waiting $delayEndDelay minutes"
			runIn(delayEndDelay*60, cycleDone)
		}
		else
		{
			handleCycleEnd()
		}

	}
}


def cycleDone() {
	if (atomicState.cycleEnd != -1 && now() - atomicState.cycleEnd > (delayEndDelay*60)-10000) {
		handleCycleEnd()
    }
    else {
    	if (debugOutput) log.debug "Power resumed during timeout"
    }
}


def delayPowerThreshold() {
	def answer = false
	
	if (!state.startedAt) {
	    state.startedAt = now()
	    answer = true
	} else {
	    def startTimeThresholdMsec = startTimeThreshold * 60000
	    def duration = now() - state.startedAt
	    if (startTimeThresholdMsec > duration) {
	        answer = true
	    }
	}

    return answer
}


def accelerationHandler(evt) {
	latestAccel = (evt.value == 'active') ? true : false
	if (debugOutput) log.debug "$evt.value, isRunning: $state.isRunning, evt: $latestAccel"

	if (!state.isRunning && latestAccel) { 
		if (descTextEnable) log.info "Cycle started, arming detector"
		state.isRunning = true
		state.startedAt = now()
		atomicState.cycleOn = true
		atomicState.cycleStart = now()
		updateMyLabel()
        if (cycleMax) { // start the deadman timer
		    def delay = Math.floor(cycleMax * 60).toInteger()
		    runIn(delay, checkCycleMax)
        }
		if (switchList) switchList*.on()
		send(messageStart)
	}
	//first time we are go inactive, hold and wait for X more.
	else if (state.isRunning && !latestAccel && state.accelOffDelay < (delayEndAcc-1)) {
		state.accelOffDelay++
		if (debugOutput) log.debug "We hit Acceleration Delay ${state.accelOffDelay} times"
	}
	//Reset Delay if it only happened once
	else if (state.isRunning && latestAccel && state.accelOffDelay != 0) {
		if (debugOutput) log.debug "We hit the Acceleration Delay ${state.accelOffDelay} times but cleared it"
		state.accelOffDelay = 0;
	}
	// If the Machine stops drawing power for X times in a row, the cycle is complete, send notification.
	else if (state.isRunning && !latestAccel) {
		send(message)
		state.isRunning = false
		atomicState.cycleEnd = now()
		atomicState.cycleOn = false
		state.accelOffDelay = 0
		updateMyLabel()
		if (descTextEnable) log.info "Cycle finished."
		if(switchList) { switchList*.off() }
	}

}


/*
	checkCycleMax
    
	If acceleration is being used, isRunning will be true.
	If power is being used, cycleOn will be true. 
	IF contact is being used, cycleOn & isRunning will be true. 
	
*/
def checkCycleMax() {
	if (state.isRunning) {
		send(message)
		state.isRunning = false
		atomicState.cycleEnd = now()
		atomicState.cycleOn = false
		state.accelOffDelay = 0
		updateMyLabel()
		if (descTextEnable) log.info "Cycle finished by deadman timer. State: ${state.isRunning}"
		if(switchList) { switchList*.off() }
	}
	if (atomicState.cycleOn) {
		send(message)
		atomicState.cycleOn = false
		atomicState.cycleEnd = now()
		atomicState.powerOffDelay = 0
		updateMyLabel()
		if (descTextEnable) log.info "Cycle finished by deadman timer. State: ${atomicState.cycleOn}"
		if(switchList) { switchList*.off() }
	}
}


// Thanks to ritchierich for these Acceleration methods
def accelerationActiveHandler(evt) {
	if (debugOutput) log.debug "vibration, $evt.value"
	if (!state.isRunning) {
		if (descTextEnable) log.info "Cycle started, arming detector"
		state.isRunning = true
		state.startedAt = now()
		atomicState.cycleStart = now()
		atomicState.cycleOn = true
		updateMyLabel()
        if (cycleMax) { // start the deadman timer
		    def delay = Math.floor(cycleMax * 60).toInteger()
		    runIn(delay, checkCycleMax)
        }
		if (switchList) switchList*.on()
		send(messageStart)
	}
	state.stoppedAt = null
}


def accelerationInactiveHandler(evt) {
	if (debugOutput) log.debug "no vibration, $evt.value, isRunning: $state.isRunning, $state.accelOffDelay"
	if (state.isRunning && state.accelOffDelay >= (delayEndAcc)) {
		if (!state.stoppedAt) {
			state.stoppedAt = now()
			atomicState.cycleEnd = now()
			atomicState.cycleOn = false
			updateMyLabel()
            def delay = fillTime ? Math.floor(fillTime * 60).toInteger() : 2
			runIn(delay, checkRunningAccel, [overwrite: false])
		}
		if (descTextEnable) log.info "Cycle finished, startedAt: ${state.startedAt}, stoppedAt: ${state.stoppedAt}"
	}
}


def contactHandler(evt) {
	latestContact = (evt.value == 'open') ? true : false
	def delayEPloop = contCycleCount-1 

	if (latestContact) {
		if (!state.isRunning) {
			if (descTextEnable) log.info "Cycle started, arming detector"
			state.isRunning = true
			state.startedAt = now()
			atomicState.cycleStart = now()
			atomicState.cycleOn = true
			updateMyLabel()
			if (cycleMax) { // start the deadman timer
			    def delay = Math.floor(cycleMax * 60).toInteger()
			    runIn(delay, checkCycleMax)
			}
			if (switchList) switchList*.on()
			send(messageStart)
		}
		state.stoppedAt = null
	}
	else if (!latestContact) {
	//first time we are below the threshold, hold and wait for X more.
		if (atomicState.cycleOn && atomicState.contOffDelay < delayEPloop){
			atomicState.contOffDelay++
			if (debugOutput) log.debug "We hit Contact Delay ${atomicState.contOffDelay} times"
		}
		else if (state.isRunning && state.contOffDelay >= (delayEPloop)) {
			if (!state.stoppedAt) {
				state.stoppedAt = now()
				atomicState.cycleEnd = now()
				atomicState.cycleOn = false
				updateMyLabel()
			}
			if (descTextEnable) log.info "Cycle finished, startedAt: ${state.startedAt}, stoppedAt: ${state.stoppedAt}"
		}
	}

	if (debugOutput) log.debug "Contact Event: $evt.value, isRunning: $state.isRunning, $state.contOffDelay, latestContact: $latestContact, startTimeThreshold: $startTimeThreshold"
}


def checkRunningAccel() {
	if (debugOutput) log.debug "checkRunning() $state.accelOffDelay"
	if (state.isRunning) {
		// def fillTimeMsec = fillTime ? fillTime * 60000 : 300000
		def fillTimeMsec = fillTime ? fillTime * 60000 : 2000
		def sensorStates = accelSensor.statesSince("acceleration", new Date((now() - fillTimeMsec) as Long))

		if (!sensorStates.find{it.value == "active"}) {
			def cycleTimeMsec = cycleTime ? cycleTime * 60000 : 600000
			def duration = now() - state.startedAt
			if (duration - fillTimeMsec > cycleTimeMsec) {
		//		if(switchList) { switchList*.off() }
				atomicState.cycleEnd = now()
				if (debugOutput) log.debug "Sending cycle complete notification"
				send(message)
			} else {
				if (debugOutput) log.debug "Not sending notification because machine wasn't running long enough $duration versus $cycleTimeMsec msec"
				state.accelOffDelay = 0
				atomicState.cycleEnd = null		// Change label to "idle"
			}
			state.isRunning = false
			atomicState.cycleOn = false
			updateMyLabel()
			if (switchList)  switchList*.off()
           		if (descTextEnable) log.info "Disarming detector"
		} else {
			if (debugOutput) log.debug "skipping notification because vibration detected again"
			state.accelOffDelay++
		}
	} else {
		if (debugOutput) log.debug "machine no longer running"
	}
}


def checkRunningCont() {
	if (debugOutput) log.debug "checkRunning() $state.contOffDelay"
	if (state.isRunning) {
		// def startTimeThresholdMsec = startTimeThreshold ? startTimeThreshold * 60000 : 300000
		def startTimeThresholdMsec = startTimeThreshold ? startTimeThreshold * 60000 : 2000
		def sensorStates = contactSensor.statesSince("contactSensor", new Date((now() - startTimeThresholdMsec) as Long))

		if (!sensorStates.find{it.value == "open"}) {
			def cycleTimeMsec = cycleTime ? cycleTime * 60000 : 600000
			def duration = now() - state.startedAt
			if (duration - startTimeThresholdMsec > cycleTimeMsec) {
		//		if(switchList) { switchList*.off() }
				atomicState.cycleEnd = now()
				if (descTextEnable) log.info "Sending cycle complete notification"
				send(message)
			} else {
				if (debugOutput) log.debug "Not sending notification because machine wasn't running long enough $duration versus $cycleTimeMsec msec"
				state.contOffDelay = 0
				atomicState.cycleEnd = null		// Change label to "idle"
			}
			state.isRunning = false
			atomicState.cycleOn = false
			updateMyLabel()
			if (switchList)  switchList*.off()
           		if (descTextEnable) log.info "Disarming detector"
		} else {
			if (debugOutput) log.debug "skipping notification because contact detected again"
			state.contOffDelay++
		}
	} else {
		if (debugOutput) log.debug "machine no longer running"
	}
}


private send(msg) {
	if (!msg) return // no message 
	if (textNotification) { textNotification*.deviceNotification(msg) }
	if (debugOutput) { log.debug "send: $msg" }
	if (state.blockItState) return // no noise please.
	if (speechOut) { speechOut*.speak(msg) }
	if (player){ player*.playText(msg) }
}


def installed() {
	// Initialize the states only when first installed...
	atomicState.cycleOn = null		// we don't know if we're running yet
	atomicState.cycleEnding = null
	state.isRunning = null
	if (switchList) switchList*.off() 
	atomicState.powerOffDelay = 0
	state.accelOffDelay = 0 
	state.contOffDelay = 0 
	
	initialize()
	app.clearSetting("debugOutput")	// app.updateSetting() only updates, won't create.
	app.clearSetting("descTextEnable")
	app.updateSetting("userSensor", "")
	if (descTextEnable) log.info "Installed with settings: ${settings}"
}


def updated() {
	unsubscribe()
	unschedule()
	initialize()
	if (blockIt) {subscribe(blockIt, "switch", blockItHandler)}
	if (descTextEnable) log.info "Updated with settings: ${settings}"
	if (!state?.calibration?.status) state.calibration.status = ""
}


def initialize() {
	if (atomicState.isPaused) {
		updateMyLabel()
		return
	}
	reSubscribe()

	schedule("17 5 0 * * ?", updateMyLabel)	// Fix the date string after the day changes
	updateMyLabel()
	
///	app.clearSetting("debugOutput")	// app.updateSetting() only updates, won't create.
///	app.clearSetting("descTextEnable") // un-comment these, click Done then replace the // comment
///	app.clearSetting("userSensor")

}


def reSubscribe() {
	if (settings.deviceType == "powerMeter" || userAttribType == "powerMeter") {
		unsubscribe(accelSensor)
		unsubscribe(contactSensor)
		subscribe(pwrMeter, "power", powerHandler)
		if (debugOutput) log.debug "Cycle: ${atomicState.cycleOn} thresholds: ${startThreshold} ${endThreshold} ${delayEndPwr}/${delayEndAcc}"
	} 
	else if (settings.deviceType == "accelerationSensor" || userAttribType == "accelerationSensor") {
		unsubscribe(pwrMeter)
		unsubscribe(contactSensor)
		subscribe(accelSensor, "acceleration", accelerationHandler)
	}
	else if (settings.deviceType == "accelSensor" || userAttribType == "accelSensor") {
		unsubscribe(pwrMeter)
		unsubscribe(contactSensor)
		subscribe(accelSensor, "acceleration.active", accelerationActiveHandler)
		subscribe(accelSensor, "acceleration.inactive", accelerationInactiveHandler)
	}
	else if (settings.deviceType == "contactSensor" || userAttribType == "contactSensor") {
		unsubscribe(pwrMeter)
		unsubscribe(accelSensor)
		subscribe(contactSensor, "contact.open", contactHandler)
		subscribe(contactSensor, "contact.closed", contactHandler)
		if (debugOutput) log.debug "Cycle: ${atomicState.cycleOn} thresholds: ${contCycleCount}"
	} 
}


def appButtonHandler(btn) {
    switch(btn) {
        case "pauseButton":
		atomicState.isPaused = true
		updateMyLabel()
        break
        case "resumeButton":
		atomicState.isPaused = false
		updateMyLabel()
        break
		case "resetButton":
        	state.isRunning = false
		atomicState.cycleEnd = now()
		atomicState.cycleOn = false
		state.accelOffDelay = 0
		state.contOffDelay = 0
		atomicState.cycleEnd = -1
		atomicState.cycleEnding = false
		clearWrinkle()
		updateMyLabel()
		unschedule(checkCycleMax)
		if (debugOutput) log.debug "Reset to Cycle finished."
		if(switchList) { switchList*.off() }
        break
		case "applyCalButton":
			applyRecommendedThresholds()
		break
		case "parseLogsButton":
			parsePowerLogsAndRecommend()
		break
		case "resetCalButton":
			resetCalibrationStats()
		break
    }
}


def blockItHandler(evt) {
	state?.blockItState = evt.value ? true : false
}


def setDebug(dbg, inf) {
	app.updateSetting("debugOutput",[value:dbg, type:"bool"])
	app.updateSetting("descTextEnable",[value:inf, type:"bool"])
	if (descTextEnable) log.info "debugOutput: $debugOutput, descTextEnable: $descTextEnable"
}


def display()
{
	section {
		paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
		paragraph "<div style='color:#1A77C9;text-align:center;font-weight:small;font-size:9px'>Developed by: Kevin Tierney, ChrisUthe, C Steele, Barry Burke<br/>Version: ${version()} -  ${thisCopyright}</div>"
    }
}

def calibrationSample(powerVal) {
	if (!calibrationEnabled) return
	state.calibration = state.calibration ?: [:]
	if (powerVal == null) return
	if (!atomicState.cycleOn) {
		updateStatsKey("idleStats", powerVal)
	} else {
		def lower = (startThreshold ?: (state.calibration?.recommended?.startThreshold ?: 0)) as Double
		def upperOk = (ignoreThreshold ? (powerVal < ignoreThreshold) : true)
		if (powerVal >= lower && upperOk) {
			updateStatsKey("activeStats", powerVal)
		}
	}
	recommendThresholds()
	if (calibrationAutoApply) applyRecommendedThresholds()
	updateCalibrationStatus()
}

def updateCalTiming(powerVal) {
	if (!calibrationEnabled) return
	state.calibration = state.calibration ?: [:]
	state.calibration.meta = state.calibration.meta ?: [:]
	def meta = state.calibration.meta
	def nowTs = now()
	if (meta.lastEventTs) {
		def intervalSec = Math.max(0.1d, (nowTs - meta.lastEventTs) / 1000d)
		def prev = meta.avgIntervalSec ?: intervalSec
		meta.avgIntervalSec = (prev * 0.8d) + (intervalSec * 0.2d)
	}
	meta.lastEventTs = nowTs
	// Track mid-cycle below-threshold gaps to suggest end-delay
	if (atomicState.cycleOn) {
		if (powerVal != null && powerVal < (endThreshold ?: 0)) {
			if (!meta.belowStartTs) meta.belowStartTs = nowTs
		} else if (meta.belowStartTs) {
			def durSec = Math.max(0d, (nowTs - meta.belowStartTs) / 1000d)
			meta.maxBelowDurationSec = Math.max(meta.maxBelowDurationSec ?: 0d, durSec)
			meta.belowStartTs = null
		}
	} else {
		meta.belowStartTs = null
	}
	state.calibration.meta = meta
}

def canCheckEndNow() {
	if (!minEndDetectMins || !atomicState.cycleStart) return true
	def minMillis = (minEndDetectMins as Double) * 60000d
	return (now() - atomicState.cycleStart) >= minMillis
}

def resetCalibrationStats() {
	state.calibration = [:]
	updateCalibrationStatus()
	if (descTextEnable) log.info "Calibration stats reset."
}

def updateCalibrationStatus() {
	def idleCnt = state.calibration?.idleStats?.count ?: 0
	def activeCnt = state.calibration?.activeStats?.count ?: 0
	state.calibration.status = "idle samples: ${idleCnt}, active samples: ${activeCnt}, updated ${new Date().format('h:mma').toLowerCase()}"
}

def isCalibrationSparse() {
	def idleCnt = state.calibration?.idleStats?.count ?: 0
	def activeCnt = state.calibration?.activeStats?.count ?: 0
	return (idleCnt < 10 || activeCnt < 10)
}

def handleCycleEnd() {
	if (wrinkleEnabled() && state.wrinkleActive && state.wrinkleRunning) {
		state.wrinkleCount = (state.wrinkleCount ?: 0) + 1
		state.wrinkleRunning = false
		atomicState.powerOffDelay = 0
		atomicState.cycleEnding = false
		if (descTextEnable) log.info "Wrinkle cycle count: ${state.wrinkleCount}"
		if (state.wrinkleCount >= (wrinkleCycleCount as Integer)) {
			send(wrinkleMessage ?: message)
			clearWrinkle()
		}
	} else {
		send(message)
		atomicState.cycleOn = false
		updateMyLabel()
		atomicState.powerOffDelay = 0
		state.remove("startedAt")
		atomicState.cycleEnd = -1
		atomicState.cycleEnding = false
		if (descTextEnable) log.info "Cycle finished."
		if (switchList) { switchList*.off() }
		if (wrinkleEnabled() && !state.wrinkleActive) {
			state.wrinkleActive = true
			state.wrinkleRunning = false
			state.wrinkleCount = 0
			state.wrinkleWindowStart = now()
			if (descTextEnable) log.info "Wrinkle monitoring started."
		}
	}
}

def wrinkleEnabled() {
	return (wrinkleCycleCount != null && (wrinkleCycleCount as Integer) > 0)
}

def checkWrinkleExpiry() {
	if (!state.wrinkleActive) return
	def mins = (wrinkleWindowMins ?: 120) as Integer
	if (now() - (state.wrinkleWindowStart ?: now()) > (mins * 60000)) {
		clearWrinkle()
	}
}

def clearWrinkle() {
	state.wrinkleActive = false
	state.wrinkleRunning = false
	state.wrinkleCount = 0
	state.wrinkleWindowStart = null
	if (descTextEnable) log.info "Wrinkle monitoring ended."
}

def recommendThresholds() {
	def cal = state.calibration ?: [:]
	def idleStats = cal.idleStats ?: cal.stats
	def activeStats = cal.activeStats
	def idleMean = idleStats?.mean
	def idleStd = statsStd(idleStats)
	def activeMean = activeStats?.mean
	def activeMax = activeStats?.max
	if (idleMean == null) return
	def idleMax = idleStats?.max ?: idleMean
	def start = idleMean + Math.max(2d, 3d * idleStd)
	def endv = idleMean + Math.max(1d, 2d * idleStd)
	// Enforce floors and keep above observed idle spikes
	start = Math.max(start, idleMax + 1.5d)
	endv = Math.max(endv, idleMax + 0.5d)
	start = Math.max(start, 5d)
	endv = Math.max(endv, 2d)
	if (endv >= start) endv = Math.max(2d, start - 0.5d)
	def ignore
	if (activeMean != null) {
		ignore = Math.max(activeMean * 2d, activeMax ? (activeMax * 1.2d) : 0d)
		ignore = Math.max(ignore, 300d)
	} else {
		ignore = Math.max(1500d, idleMean + (10d * idleStd))
	}
	ignore = Math.max(ignore, start + 50d)
	cal.recommended = [
		startThreshold: round1(start),
		endThreshold: round1(endv),
		ignoreThreshold: round1(ignore)
	]
	// Delay suggestions based on observed reporting interval and mid-cycle dips
	def meta = cal.meta ?: [:]
	def avgInt = meta.avgIntervalSec ?: 0d
	def maxBelow = meta.maxBelowDurationSec ?: 0d
	def delayMins = maxBelow > 0 ? Math.max(1, Math.round((maxBelow / 60d) + 1d) as Integer) : null
	def delayCount = avgInt > 0 ? Math.max(2, Math.round((120d / avgInt)) as Integer) : null
	cal.recommended.delayEndDelay = delayMins
	cal.recommended.delayEndPwr = delayCount
	state.calibration = cal
}

def applyRecommendedThresholds() {
	def rec = state?.calibration?.recommended
	if (!rec) return
	app.updateSetting("startThreshold",[value: "${rec.startThreshold}", type:"decimal"])
	app.updateSetting("endThreshold",[value: "${rec.endThreshold}", type:"decimal"])
	app.updateSetting("ignoreThreshold",[value: "${rec.ignoreThreshold}", type:"decimal"])
	if (rec.delayEndPwr != null) {
		app.updateSetting("delayEndPwr",[value: "${rec.delayEndPwr}", type:"number"])
	}
	if (rec.delayEndDelay != null) {
		app.updateSetting("delayEndDelay",[value: "${rec.delayEndDelay}", type:"number"])
	}
	if (descTextEnable) log.info "Applied recommended thresholds: ${rec}"
}

def parsePowerLogsAndRecommend() {
	if (!powerLogText) return
	def values = []
	powerLogText.eachLine { line ->
		def m = (line =~ /power is ([0-9.]+) W/)
		if (m.find()) {
			values << (m.group(1) as Double)
		}
	}
	if (!values) return
	values = values.take(500)
	state.calibration = state.calibration ?: [:]
	state.calibration.idleStats = buildStats(values)
	recommendThresholds()
	if (calibrationAutoApply) applyRecommendedThresholds()
	updateCalibrationStatus()
	if (descTextEnable) log.info "Parsed ${values.size()} power samples from logs"
}

def updateStatsKey(key, powerVal) {
	state.calibration = state.calibration ?: [:]
	def stats = state.calibration[key] ?: [count:0, mean:0d, m2:0d, min:999999d, max:0d]
	def v = powerVal as Double
	stats.count = (stats.count ?: 0) + 1
	def delta = v - stats.mean
	stats.mean = stats.mean + (delta / stats.count)
	def delta2 = v - stats.mean
	stats.m2 = stats.m2 + (delta * delta2)
	stats.min = Math.min(stats.min ?: v, v)
	stats.max = Math.max(stats.max ?: v, v)
	state.calibration[key] = stats
}

def buildStats(list) {
	def stats = [count:0, mean:0d, m2:0d, min:999999d, max:0d]
	list.each { v ->
		def d = v as Double
		stats.count = stats.count + 1
		def delta = d - stats.mean
		stats.mean = stats.mean + (delta / stats.count)
		def delta2 = d - stats.mean
		stats.m2 = stats.m2 + (delta * delta2)
		stats.min = Math.min(stats.min, d)
		stats.max = Math.max(stats.max, d)
	}
	return stats
}

def statsStd(stats) {
	if (!stats?.count || stats.count < 2) return 0d
	def variance = stats.m2 / (stats.count - 1)
	return Math.sqrt(Math.max(0d, variance))
}

def round1(v) {
	return (Math.round((v as Double) * 10d) / 10d)
}



void updateMyLabel() {
	boolean ST = false
	String flag = '<span '
	
	// Display Ecobee connection status as part of the label...
	String myLabel = atomicState.appDisplayName
	if ((myLabel == null) || !app.label.startsWith(myLabel)) {
		myLabel = app.label ?: app.name
		if (!myLabel.contains(flag)) atomicState.appDisplayName = myLabel
	} 
	if (myLabel.contains(flag)) {
		// strip off any connection status tag
		myLabel = myLabel.substring(0, myLabel.indexOf(flag))
		atomicState.appDisplayName = myLabel
	}
	String newLabel
	if (atomicState.isPaused) {
		newLabel = myLabel + '<span style="color:Crimson"> (paused)</span>'
	} else if (atomicState.cycleOn) {
		String beganAt = atomicState.cycleStart ? "started " + fixDateTimeString(atomicState.cycleStart) : 'running'
		newLabel = myLabel + "<span style=\"color:Green\"> (${beganAt})</span>"
	} else if ((atomicState.cycleOn != null) && (atomicState.cycleOn == false)) {
		String endedAt = atomicState.cycleEnd ? "finished " + fixDateTimeString(atomicState.cycleEnd) : 'idle'
		newLabel = myLabel + "<span style=\"color:Green\"> (${endedAt})</span>"
	} else {
		newLabel = myLabel
	}
	if (app.label != newLabel) app.updateLabel(newLabel)
}

				   
String fixDateTimeString( eventDate) {
	def today = new Date(now()).clearTime()
	def target = new Date(eventDate).clearTime()
	
	String resultStr = ''
	String myDate = ''
	String myTime = ''
	boolean showTime = true
	
	if (target == today) {
		myDate = 'today'	
	} else if (target == today-1) {
		myDate = 'yesterday'
	} else if (target == today+1) {
		myDate = 'tomorrow'
	} else if (dateStr == '2035-01-01' ) {		// to Infinity
		myDate = 'a long time from now'
		showTime = false
	} else {
		myDate = 'on '+target.format('MM-dd')
	}	 
	if (showTime) {
		myTime = new Date(eventDate).format('h:mma').toLowerCase()
	}
	if (myDate || myTime) {
		resultStr = myTime ? "${myDate} at ${myTime}" : "${myDate}"
	}
	if (debugOutput) { log.debug "banner: ${resultStr}"}
	return resultStr
}


def getThisCopyright(){"&copy; 2019 C Steele "}
