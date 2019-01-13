/**
 *  Hubitat Import URL: https://raw.githubusercontent.com/HubitatCommunity/Hubitat-BetterLaundryMonitor/master/BetterLaundryMonitor_Parent.groovy
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

definition(
    name: "Better Laundry Monitor",
    namespace: "tierneykev",
    author: "Kevin Tierney",
    description: "Using a switch with powerMonitor capability, monitor the laundry cycle and alert when it's done.",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartthings-device-icons/Appliances/appliances8-icn.png",
    iconX2Url: "https://s3.amazonaws.com/smartthings-device-icons/Appliances/appliances8-icn@2x.png")


preferences {
     page name: "mainPage", title: "", install: true, uninstall: true // ,submitOnChange: true      
} 

// App Version   ***** with great thanks and acknowlegment to Cobra (CobraVmax) for his original version checking code ********
def setAppVersion(){
     state.version = "1.2"
     state.InternalName = "BLMparent"
     state.Type = "Application"
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unschedule()
    unsubscribe()
    initialize()
}

def initialize() {
    version()
    log.info "There are ${childApps.size()} child smartapps"
    childApps.each {child ->
    log.info "Child app: ${child.label}"
    }
}

def mainPage() {
    dynamicPage(name: "mainPage") {
      display()
	section {    
			paragraph title: "<Better Laundry Monitor",
			"<b>This parent app is a container for all:</b><br> Better Laundry Monitor - Power Switch child apps"
	}
      section (){app(name: "BlMpSw", appName: "Better Laundry Monitor - Power Switch", namespace: "tierneykev", title: "New Better Laundry Monitor - Power Switch App", multiple: true)}    
        
      section (title: "<b>Name/Rename</b>") {label title: "Enter a name for this parent app (optional)", required: false}
 } 
}


// Check Version   ***** with great thanks and acknowlegment to Cobra (CobraVmax) for his original version checking code ********
def version(){
    updatecheck()
    if (state.Type == "Application") { schedule("0 0 14 ? * FRI *", updatecheck) }
    if (state.Type == "Driver") { schedule("0 45 16 ? * MON *", updatecheck) }
}

def display(){
    version()
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
