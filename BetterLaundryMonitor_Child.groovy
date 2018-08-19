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
     state.version = "1.2"
     state.InternalName = "BLMchild"
     state.Type = "Application"
}

def mainPage() {
    dynamicPage(name: "mainPage") {
      display()
      section ("<b>When this device stops drawing power</b>") {
        input "meter", "capability.powerMeter", multiple: false, required: true
      }
      section ("<b>Power Thresholds</b>", hidden: false, hideable: true) {
        input "startThreshold", "decimal", title: "start cycle when power raises above (W)", description: "8", required: false
        input "endThreshold", "decimal", title: "stop cycle when power drops below (W)", description: "4", required: false
        input "delayEnd", "number", title: "stop only after the power has been below the threashold for this many reportings:", description: "2", required: false
      }
    
      section ("<b>Send this message</b>") {
        input "message", "text", title: "Notification message", description: "Laundry is done!", required: true
      }
    
      section (title: "<b>Using this Notification Method</b>") {
        input "sendPushMessage", "bool", title: "Send a push notification?"
        input "speechOut", "capability.speechSynthesis", title:"Speak Via: (Speech Synthesis)",multiple: true, required: false
        input "player", "capability.musicPlayer", title:"Speak Via: (Music Player -> TTS)",multiple: true, required: false
        input "phone", "phone", title: "Send a text message to:", required: false
      }
      section (title: "<b>Name/Rename</b>") {label title: "This child app's Name (optional)", required: false}
   }
}

def installed() {
  initialize()
  log.debug "Installed with settings: ${settings}"
}

def updated() {
  unsubscribe()
  initialize()
  log.debug "Updated with settings: ${settings}"
}

def initialize() {
  subscribe(meter, "power", handler)
  atomicState.cycleOn = false
  atomicState.powerOffDelay = 0
  startThreshold = startThreshold ?: 8
  endThreshold = endThreshold ?: 4
  delayEnd = delayEnd ?: 2
  schedule("0 0 14 ? * FRI *", updatecheck)
  version()
  log.trace "thresholds: ${startThreshold} ${endThreshold} ${delayEnd}"
}

def handler(evt) {
  def latestPower = meter.currentValue("power")
  log.trace "Power: ${latestPower}W"
  log.trace "State: ${atomicState.cycleOn}"

  //Added latestpower < 1000 to deal with spikes that triggered false alarms
  if (!atomicState.cycleOn && latestPower >= startThreshold && latestPower) {
    atomicState.cycleOn = true   
    log.trace "Cycle started."
  }
      //first time we are below the threashhold, hold and wait for a second.
      else if (atomicState.cycleOn && latestPower < endThreshold && atomicState.powerOffDelay < delayEnd){
      	atomicState.powerOffDelay = atomicState.powerOffDelay + 1
          log.trace "We hit delay ${atomicState.powerOffDelay} times"
      }
        //Reset Delay if it only happened once
      else if (atomicState.cycleOn && latestPower >= endThreshold && atomicState.powerOffDelay != 0) {
          log.trace "We hit the delay ${atomicState.powerOffDelay} times but cleared it"
          atomicState.powerOffDelay = 0;
          
        }
      // If the Machine stops drawing power for X times in a row, the cycle is complete, send notification.
      else if (atomicState.cycleOn && latestPower < endThreshold) {
        send(message)
        if(speechOut){speakMessage(message)}
        if(player){musicPlayerTTS(message)}
        atomicState.cycleOn = false
        atomicState.cycleEnd = now()
        atomicState.powerOffDelay = 0
        log.trace "State: ${atomicState.cycleOn}"
  }
}

private send(msg) {
  if (sendPushMessage) {
    sendPush(msg)
  }

  if (phone) {
    sendSms(phone, msg)
  }

  log.debug msg
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
    def paramsUD = [uri: "https://raw.githubusercontent.com/HubitatCommunity/Hubitat-BetterLaundryMonitor/master/versions.json"]
       try {
        httpGet(paramsUD) { respUD ->
 //  log.info " Version Checking - Response Data: ${respUD.data}"
       def copyNow = (respUD.data.copyright)
       state.Copyright = copyNow
            def newver = (respUD.data.versions.(state.Type).(state.InternalName))
            def updatecheckVer = (respUD.data.versions.(state.Type).(state.InternalName).replace(".", ""))
       def updatecheckOld = state.version.replace(".", "")
       if(updatecheckOld < updatecheckVer){
		state.Status = "<b>** New Version Available (Version: $newver) **</b>"
           log.warn "** There is a newer version of this $state.Type available  (Version: $newver) **"
       }    
       else{ 
      state.Status = "Current"
      log.info "$state.Type is the current version"
       }
       
       }
        } 
        catch (e) {
        log.error "Something went wrong: $e"
    }
}        
