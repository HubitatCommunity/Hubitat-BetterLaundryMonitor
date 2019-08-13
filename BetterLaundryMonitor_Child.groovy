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
	public static String version()      {  return "v1.4.0"  }


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
			input (name: "deviceType", title: "Type of Device", type: "enum", options: [powerMeter:"Power Meter", accelerationSensor:"Acceleration Sensor"], required:true, submitOnChange:true)
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
		if (deviceType == "accelerationSensor") {
			section("<b>When vibration stops on this device</b>") {
				input "accelSensor", "capability.accelerationSensor", title: "Acceleration Sensor" , multiple: false, required: false, defaultValue: null
			}
		}
	}
}


def thresholdPage() {
	dynamicPage(name: "thresholdPage") {
		if (deviceType == "accelerationSensor") {
			section("<b>Time Thresholds (in minutes)</b>", hidden: false, hideable: true) {
				input "cycleTime", "decimal", title: "Minimum cycle time", required: false, defaultValue: 10
				input "fillTime", "decimal", title: "Time to fill tub", required: false, defaultValue: 5
			}
		}
		if (deviceType == "powerMeter") {
			section ("<b>Power Thresholds</b>", hidden: false, hideable: true) {
				input "startThreshold", "decimal", title: "Start cycle when power raises above (W)", defaultValue: "8", required: false
				input "endThreshold", "decimal", title: "Stop cycle when power drops below (W)", defaultValue: "4", required: false
				input "delayEnd", "number", title: "Stop only after the power has been below the threshold for this many reportings:", defaultValue: "2", required: false
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
			input "phone", "phone", title: "SMS Via: <i>(phone number)</i>", required: false
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
		thresholdPage: cycleTime ?: fillTime ?: startThreshold ?: endThreshold ?: delayEnd,
		informPage: messageStart?.size() ?: message?.size()
	]
	status << [all: status.sensorPage ?: status.thresholdPage ?: status.informPage]
}


def powerHandler(evt) {
	def latestPower = pwrMeter.currentValue("power")	
	if (debugOutput) log.debug "Power: ${latestPower}W, State: ${atomicState.cycleOn}, thresholds: ${startThreshold} ${endThreshold} ${delayEnd}"
	
	if (!atomicState.cycleOn && latestPower >= startThreshold && latestPower < 10000) { // latestpower < 1000: eliminate spikes that trigger false alarms
		send(messageStart)
		atomicState.cycleOn = true   
		if (debugOutput) log.debug "Cycle started. State: ${atomicState.cycleOn}"
		if(switchList) { switchList.on() }
	}
		//first time we are below the threshhold, hold and wait for X more.
		else if (atomicState.cycleOn && latestPower < endThreshold && atomicState.powerOffDelay < (delayEnd -1)){
			atomicState.powerOffDelay++
			if (debugOutput) log.debug "We hit delay ${atomicState.powerOffDelay} times"
		}
		//Reset Delay if it only happened once
		else if (atomicState.cycleOn && latestPower >= endThreshold && atomicState.powerOffDelay != 0) {
			atomicState.powerOffDelay = 0;
			if (debugOutput) log.debug "We hit the delay ${atomicState.powerOffDelay} times but cleared it"
		    
		}
		// If the Machine stops drawing power for X times in a row, the cycle is complete, send notification.
		else if (atomicState.cycleOn && latestPower < endThreshold) {
			send(message)
			atomicState.cycleOn = false
			atomicState.cycleEnd = now()
			atomicState.powerOffDelay = 0
			if (debugOutput) log.debug "State: ${atomicState.cycleOn}"
			if(switchList) { switchList.off() }
	}
}


// Thanks to ritchierich for these Acceleration methods
def accelerationActiveHandler(evt) {
	if (debugOutput) log.debug "vibration"
	if (!state.isRunning) {
		if (debugOutput) log.debug "Arming detector"
		state.isRunning = true
		state.startedAt = now()
		if(switchList) { switchList.on() }
		send(messageStart)
	}
	state.stoppedAt = null
}


def accelerationInactiveHandler(evt) {
	if (debugOutput) log.debug "no vibration, isRunning: $state.isRunning"
	if (state.isRunning) {
		if (!state.stoppedAt) {
			state.stoppedAt = now()
            	def delay = Math.floor(fillTime * 60).toInteger()
			runIn(delay, checkRunning, [overwrite: false])
		}
		if (debugOutput) log.debug "startedAt: ${state.startedAt}, stoppedAt: ${state.stoppedAt}"
	}
}


def checkRunning() {
	if (debugOutput) log.debug "checkRunning()"
	if (state.isRunning) {
		def fillTimeMsec = fillTime ? fillTime * 60000 : 300000
		def sensorStates = accelSensor.statesSince("acceleration", new Date((now() - fillTimeMsec) as Long))

		if (!sensorStates.find{it.value == "active"}) {
			def cycleTimeMsec = cycleTime ? cycleTime * 60000 : 600000
			def duration = now() - state.startedAt
			if (duration - fillTimeMsec > cycleTimeMsec) {
				if(switchList) { switchList.off() }
				if (debugOutput) log.debug "Sending notification"
				send(message)
			} else {
				if (debugOutput) log.debug "Not sending notification because machine wasn't running long enough $duration versus $cycleTimeMsec msec"
			}
			state.isRunning = false
			if (debugOutput) log.debug "Disarming detector"
		} else {
			if (debugOutput) log.debug "skipping notification because vibration detected again"
		}
	}
	else {
		if (debugOutput) log.debug "machine no longer running"
	}
}


private send(msg) {
	if (phone) { sendSms(phone, msg) }
	if (speechOut) { speechOut.speak(msg) }
	if (player){ player.playText(msg) }
	if (textNotification) { textNotification.deviceNotification(msg) }
	if (debugOutput) { log.debug msg }
}


def installed() {
	initialize()
	app.clearSetting("debugOutput")	// app.updateSetting() only updates, won't create.
	app.clearSetting("descTextEnable")
	if (descTextEnable) log.info "Installed with settings: ${settings}"
}


def updated() {
	initialize()
	if (descTextEnable) log.info "Updated with settings: ${settings}"
}


def initialize() {
	unsubscribe()
	if (settings.deviceType == "powerMeter") {
		subscribe(pwrMeter, "power", powerHandler)
		atomicState.cycleOn = false
		atomicState.powerOffDelay = 0
		if (debugOutput) log.debug "Cycle: ${atomicState.cycleOn} thresholds: ${startThreshold} ${endThreshold} ${delayEnd}"
	} else if (settings.deviceType == "accelerationSensor") {
		subscribe(accelSensor, "acceleration.active", accelerationActiveHandler)
		subscribe(accelSensor, "acceleration.inactive", accelerationInactiveHandler)
	}
	schedule("0 0 14 ? * FRI *", updateCheck)
	if(switchList) {switchList.off()}
//	app.clearSetting("debugOutput")	// app.updateSetting() only updates, won't create.
//	app.clearSetting("descTextEnable") // un-comment these, click Done then replace the // comment
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
		// uses reformattted 'version2.json' 
		def newVerRaw = (respUD.application.(state.InternalName).ver)
		def newVer = (respUD.application.(state.InternalName).ver.replaceAll("[.vV]", ""))
		def currentVer = version().replaceAll("[.vV]", "")                
		state.UpdateInfo = (respUD.application.(state.InternalName).updated)
		def author = (respUD.author)
            // log.debug "updateCheck: $newVerRaw, $state.UpdateInfo, $author"
	
		if(newVer == "NLS")
		{
		      state.Status = "<b>** This Application is no longer supported by $author  **</b>"       
		      log.warn "** This Application is no longer supported by $author **"      
		}           
		else if(currentVer < newVer)
		{
		      state.Status = "<b>New Version Available (Version: $newVerRaw)</b>"
		      log.warn "** There is a newer version of this Application available  (Version: $newVerRaw) **"
		      log.warn "** $state.UpdateInfo **"
		} 
		else if(currentVer > newVer)
		{
		      state.Status = "<b>You are using a Test version of this Application (Expecting: $newVerRaw)</b>"
		}
		else
		{ 
		    state.Status = "Current"
		    if (descTextEnable) log.info "You are using the current version of this Application"
		}
	
	      if(state.Status == "Current")
	      {
	           state.UpdateInfo = "N/A"
	           sendEvent(name: "ApplicationUpdate", value: state.UpdateInfo)
	           sendEvent(name: "ApplicationStatus", value: state.Status)
	      }
	      else 
	      {
	           sendEvent(name: "ApplicationUpdate", value: state.UpdateInfo)
	           sendEvent(name: "ApplicationStatus", value: state.Status)
	      }
      }
      else
      {
           log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI"
      }
}

def getThisCopyright(){"&copy; 2019 C Steele "}