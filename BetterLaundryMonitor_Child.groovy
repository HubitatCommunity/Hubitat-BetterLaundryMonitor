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
 */
	public static String version()      {  return "v1.4.4"  }


import groovy.time.*

definition(
	name: "Better Laundry Monitor - Power Switch",
	namespace: "tierneykev",
	author: "Kevin Tierney, ChrisUthe, CSteele",
	description: "Child: powerMonitor capability, monitor the laundry cycle and alert when it's done.",
	category: "Green Living",
	    
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


def mainPage() {
	dynamicPage(name: "mainPage", install: true, uninstall: true) {
		section("<h2>${app.label ?: app.name}</h2>"){}
		section("-= <b>Main Menu</b> =-") 
		{
			input (name: "deviceType", title: "Type of Device", type: "enum", options: [powerMeter:"Power Meter", accelerationSensor:"Sequence Vibration Sensor", accelSensor:"Timed Vibration Sensor"], required:true, submitOnChange:true)
		}
		if (deviceType) {
			section
			{
				href "sensorPage", title: "Sensors", description: "Sensors to be monitored", state: selectOk?.sensorPage ? "complete" : null
				href "thresholdPage", title: "Thresholds", description: "Thresholds to be monitored", state: selectOk?.thresholdPage ? "complete" : null
				href "informPage", title: "Inform", description: "Who and what to Inform", state: selectOk?.informPage ? "complete" : null
			}
		}
		section (title: "<b>Name/Rename</b>") {
			label title: "This child app's Name (optional)", required: false
		}
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
	}
}


def thresholdPage() {
	dynamicPage(name: "thresholdPage") {
		if (deviceType == "accelerationSensor") {
			section("<b>Vibration Thresholds</b>", hidden: false, hideable: true) {
				input "delayEndAcc", "number", title: "Stop after no vibration for this many sequential reportings:", defaultValue: "2", required: false
				input "cycleMax", "number", title: "Optional: Maximum cycle time (acts as a deadman timer.)", required: false
			}
		}
		if (deviceType == "powerMeter") {
			section ("<b>Power Thresholds</b>", hidden: false, hideable: true) {
				input "startThreshold", "decimal", title: "Start cycle when power raises above (W)", defaultValue: "8", required: false
				input "endThreshold", "decimal", title: "Stop cycle when power drops below (W)", defaultValue: "4", required: false
				input "delayEndPwr", "number", title: "Stop after power has been below the threshold for this many sequential reportings:", defaultValue: "2", required: false
                		input "startTimeThreshold", "number", title: "Optional: Time (in minutes) to wait before counting power threshold.  Great for pre-wash soaks.", required: false
				input "cycleMax", "number", title: "Optional: Maximum cycle time (acts as a deadman timer.)", required: false
			}
		}
		if (deviceType == "accelSensor") {
			section("<b>Time Thresholds (in minutes)</b>", hidden: false, hideable: true) {
				input "fillTime", "decimal", title: "Time to fill tub (0 for Dryer)", required: false, defaultValue: 5
				input "cycleTime", "decimal", title: "Minimum cycle time", required: false, defaultValue: 10
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
		  	input "switchList", "capability.switch", title: "Which Switches?", description: "Have a switch follow the active state", multiple: true, hideWhenEmpty: false, required: false             
		}
	}
}


def getSelectOk()
{
	def status =
	[
		sensorPage: pwrMeter ?: accelSensor,
		thresholdPage: cycleTime ?: fillTime ?: startThreshold ?: endThreshold ?: delayEndAcc ?: delayEndPwr,
		informPage: messageStart?.size() ?: message?.size()
	]
	status << [all: status.sensorPage ?: status.thresholdPage ?: status.informPage]
}


def powerHandler(evt) {
	def latestPower = pwrMeter.currentValue("power")	
	if (debugOutput) log.debug "Power: ${latestPower}W, State: ${atomicState.cycleOn}, thresholds: ${startThreshold} ${endThreshold} ${delayEndPwr}"
	
	if (!atomicState.cycleOn && latestPower >= startThreshold && latestPower < 10000) { // latestpower < 1000: eliminate spikes that trigger false alarms
		send(messageStart)
		atomicState.cycleOn = true   
		if (debugOutput) log.debug "Cycle started. State: ${atomicState.cycleOn}"
		if(switchList) { switchList.on() }
        	if (cycleMax) { // start the deadman timer
		    def delay = Math.floor(cycleMax * 60).toInteger()
		    runIn(delay, checkCycleMax)
        	}
	}
    //If Start Time Threshold was set, check if we have waited that number of minutes before counting the power thresholds
    else if (startTimeThreshold && delayPowerThreshold()) {
        //do nothing
    }
	//first time we are below the threshold, hold and wait for X more.
	else if (atomicState.cycleOn && latestPower < endThreshold && atomicState.powerOffDelay < (delayEndPwr-1)){
		atomicState.powerOffDelay++
		if (debugOutput) log.debug "We hit delay ${atomicState.powerOffDelay} times"
	}
	//Reset Delay if it only happened once
	else if (atomicState.cycleOn && latestPower >= endThreshold && atomicState.powerOffDelay != 0) {
		if (debugOutput) log.debug "We hit the delay ${atomicState.powerOffDelay} times but cleared it"
		atomicState.powerOffDelay = 0;
	    
	}
	// If the Machine stops drawing power for X times in a row, the cycle is complete, send notification.
	else if (atomicState.cycleOn && latestPower < endThreshold) {
		send(message)
		atomicState.cycleOn = false
		atomicState.cycleEnd = now()
		atomicState.powerOffDelay = 0
        	state.remove("startedAt")
		if (debugOutput) log.debug "State: ${atomicState.cycleOn}"
		if(switchList) { switchList.off() }
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
		if (debugOutput) log.debug "Arming detector, cycle started"
		state.isRunning = true
		state.startedAt = now()
        	if (cycleMax) { // start the deadman timer
		    def delay = Math.floor(cycleMax * 60).toInteger()
		    runIn(delay, checkCycleMax)
        	}
		if (switchList) switchList.on()
		send(messageStart)
	}
	//first time we are go inactive, hold and wait for X more.
	else if (state.isRunning && !latestAccel && state.accelOffDelay < (delayEndAcc-1)) {
		state.accelOffDelay++
		if (debugOutput) log.debug "We hit delay ${state.accelOffDelay} times"
	}
	//Reset Delay if it only happened once
	else if (state.isRunning && latestAccel && state.accelOffDelay != 0) {
		if (debugOutput) log.debug "We hit the delay ${state.accelOffDelay} times but cleared it"
		state.accelOffDelay = 0;
	}
	// If the Machine stops drawing power for X times in a row, the cycle is complete, send notification.
	else if (state.isRunning && !latestAccel) {
		send(message)
		state.isRunning = false
		state.cycleEnd = now()
		state.accelOffDelay = 0
		if (debugOutput) log.debug "Cycle ended. State: ${state.isRunning}"
		if(switchList) { switchList.off() }
	}

}

/*
	checkCycleMax
    
	If acceleration is being used, isRunning will be true.
	If power is being used, cycleOn will be true. 
	
*/
def checkCycleMax() {
	if (state.isRunning) {
		send(message)
		state.isRunning = false
		state.cycleEnd = now()
		state.accelOffDelay = 0
		if (debugOutput) log.debug "Cycle ended by deadman timer. State: ${state.isRunning}"
		if(switchList) { switchList.off() }
	}
	if (atomicState.cycleOn) {
		send(message)
		atomicState.cycleOn = false
		atomicState.cycleEnd = now()
		atomicState.powerOffDelay = 0
		if (debugOutput) log.debug "Cycle ended by deadman timer. State: ${atomicState.cycleOn}"
		if(switchList) { switchList.off() }
	}
}



// Thanks to ritchierich for these Acceleration methods
def accelerationActiveHandler(evt) {
	if (debugOutput) log.debug "vibration, $evt.value"
	if (!state.isRunning) {
		if (debugOutput) log.debug "Arming detector"
		state.isRunning = true
		state.startedAt = now()
        	if (cycleMax) { // start the deadman timer
		    def delay = Math.floor(cycleMax * 60).toInteger()
		    runIn(delay, checkCycleMax)
        	}
		if (switchList) switchList.on()
		send(messageStart)
	}
	state.stoppedAt = null
}


def accelerationInactiveHandler(evt) {
	if (debugOutput) log.debug "no vibration, $evt.value, isRunning: $state.isRunning, $state.accelOffDelay"
	if (state.isRunning && state.accelOffDelay >= (delayEndAcc)) {
		if (!state.stoppedAt) {
			state.stoppedAt = now()
            	def delay = fillTime ? Math.floor(fillTime * 60).toInteger() : 2
			runIn(delay, checkRunning, [overwrite: false])
		}
		if (debugOutput) log.debug "startedAt: ${state.startedAt}, stoppedAt: ${state.stoppedAt}"
	}
}


def checkRunning() {
	if (debugOutput) log.debug "checkRunning() $state.accelOffDelay"
	if (state.isRunning) {
		// def fillTimeMsec = fillTime ? fillTime * 60000 : 300000
		def fillTimeMsec = fillTime ? fillTime * 60000 : 2000
		def sensorStates = accelSensor.statesSince("acceleration", new Date((now() - fillTimeMsec) as Long))

		if (!sensorStates.find{it.value == "active"}) {
			def cycleTimeMsec = cycleTime ? cycleTime * 60000 : 600000
			def duration = now() - state.startedAt
			if (duration - fillTimeMsec > cycleTimeMsec) {
		//		if(switchList) { switchList.off() }
				if (debugOutput) log.debug "Sending notification"
				send(message)
			} else {
				if (debugOutput) log.debug "Not sending notification because machine wasn't running long enough $duration versus $cycleTimeMsec msec"
				state.accelOffDelay = 0 
			}
			state.isRunning = false
			if (switchList)  switchList.off()
           		if (debugOutput) log.debug "Disarming detector"
		} else {
			if (debugOutput) log.debug "skipping notification because vibration detected again"
			state.accelOffDelay++
		}
	} else {
		if (debugOutput) log.debug "machine no longer running"
	}
}



private send(msg) {
	if (!msg) return // no message 
	if (textNotification) { textNotification.deviceNotification(msg) }
	if (debugOutput) { log.debug "send: $msg" }
	if (state.blockItState) return // no noise please.
	if (speechOut) { speechOut.speak(msg) }
	if (player){ player.playText(msg) }
}


def installed() {
	initialize()
	app.clearSetting("debugOutput")	// app.updateSetting() only updates, won't create.
	app.clearSetting("descTextEnable")
	if (descTextEnable) log.info "Installed with settings: ${settings}"
}


def updated() {
	initialize()
	if (blockIt) {subscribe(blockIt, "switch", blockItHandler)}
	if (descTextEnable) log.info "Updated with settings: ${settings}"
}


def initialize() {
	unsubscribe()
	if (settings.deviceType == "powerMeter") {
		subscribe(pwrMeter, "power", powerHandler)
		atomicState.cycleOn = false
		state.isRunning = false
		atomicState.powerOffDelay = 0
		state.accelOffDelay = 0 
		if (debugOutput) log.debug "Cycle: ${atomicState.cycleOn} thresholds: ${startThreshold} ${endThreshold} ${delayEndPwr}/${delayEndAcc}"
	} 
	else if (settings.deviceType == "accelerationSensor") {
		subscribe(accelSensor, "acceleration", accelerationHandler)
	}
	else if (settings.deviceType == "accelSensor") {
		subscribe(accelSensor, "acceleration.active", accelerationActiveHandler)
		subscribe(accelSensor, "acceleration.inactive", accelerationInactiveHandler)
	}
	//	schedule("0 0 14 ? * FRI *", updateCheck) It's run every time it's displayed
	if(switchList) {switchList.off()}
//	app.clearSetting("debugOutput")	// app.updateSetting() only updates, won't create.
//	app.clearSetting("descTextEnable") // un-comment these, click Done then replace the // comment
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
	updateCheck()
	section {
		paragraph "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
		paragraph "<div style='color:#1A77C9;text-align:center;font-weight:small;font-size:9px'>Developed by: Kevin Tierney, ChrisUthe, C Steele<br/>Version Status: $state.Status<br>Current Version: ${version()} -  ${thisCopyright}</div>"
    }
}


// Check Version   ***** with great thanks and acknowledgment to Cobra (CobraVmax) for his original code ****
def updateCheck()
{    
	def paramsUD = [uri: "https://hubitatcommunity.github.io/Hubitat-BetterLaundryMonitor/version2.json"]
	
 	asynchttpGet("updateCheckHandler", paramsUD) 
}


def updateCheckHandler(resp, data) 
{
	state.InternalName = "BLMchild"
	
	if (resp.getStatus() == 200 || resp.getStatus() == 207) {
		respUD = parseJson(resp.data)
		//log.warn " Version Checking - Response Data: $respUD"   // Troubleshooting Debug Code - Uncommenting this line should show the JSON response from your webserver 
		state.Copyright = "${thisCopyright} -- ${version()}"
		// uses reformattted 'version2.json' 
		def newVer = padVer(respUD.application.(state.InternalName).ver)
		def currentVer = padVer(version())               
		state.UpdateInfo = (respUD.application.(state.InternalName).updated)
            // log.debug "updateCheck: ${respUD.driver.(state.InternalName).ver}, $state.UpdateInfo, ${respUD.author}"
	
		switch(newVer) {
			case { it == "NLS"}:
			      state.Status = "<b>** This Application is no longer supported by ${respUD.author}  **</b>"       
			      log.warn "** This Application is no longer supported by ${respUD.author} **"      
				break
			case { it > currentVer}:
			      state.Status = "<b>New Version Available (Version: ${respUD.application.(state.InternalName).ver})</b>"
			      log.warn "** There is a newer version of this Application available  (Version: ${respUD.application.(state.InternalName).ver}) **"
			      log.warn "** $state.UpdateInfo **"
				break
			case { it < currentVer}:
			      state.Status = "<b>You are using a Test version of this Application (Expecting: ${respUD.application.(state.InternalName).ver})</b>"
				break
			default:
				state.Status = "Current"
				if (descTextEnable) log.info "You are using the current version of this Application"
				break
		}

	      sendEvent(name: "chkUpdate", value: state.UpdateInfo)
	      sendEvent(name: "chkStatus", value: state.Status)
      }
      else
      {
           log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI"
      }
}

/*
	padVer

	Version progression of 1.4.9 to 1.4.10 would mis-compare unless each column is padded into two-digits first.

*/ 
def padVer(ver) {
	def pad = ""
	ver.replaceAll( "[vV]", "" ).split( /\./ ).each { pad += it.padLeft( 2, '0' ) }
	return pad
}

def getThisCopyright(){"&copy; 2019 C Steele "}
