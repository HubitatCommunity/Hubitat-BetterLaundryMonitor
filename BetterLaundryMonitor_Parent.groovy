/**
 *  Hubitat Import URL: https://raw.githubusercontent.com/HubitatCommunity/Hubitat-BetterLaundryMonitor/master/BetterLaundryMonitor_Parent.groovy
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
	public static String version()      {  return "v1.4.1"  }


definition(
    name: "Better Laundry Monitor",
    namespace: "tierneykev",
    author: "Kevin Tierney, CSteele",
    description: "Using a switch with powerMonitor capability, monitor the laundry cycle and alert when it starts or done.",
    category: "Green Living",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    )


preferences {
     page name: "mainPage", title: "", install: true, uninstall: true // ,submitOnChange: true      
} 


def mainPage() {
	dynamicPage(name: "mainPage") {
		section {    
			paragraph title: "<Better Laundry Monitor",
			"<b>This parent app is a container for all:</b><br> Better Laundry Monitor - Power Switch child apps"
		}
      	section (){app(name: "BlMpSw", appName: "Better Laundry Monitor - Power Switch", namespace: "tierneykev", title: "New Better Laundry Monitor - Power Switch App", multiple: true)}    
      	  
      	section (title: "<b>Name/Rename</b>") {label title: "Enter a name for this parent app (optional)", required: false}
	
		section ("Other preferences") {
			input "debugOutput",   "bool", title: "<b>Enable debug logging?</b>", defaultValue: true
			input "descTextEnable","bool", title: "<b>Enable descriptionText logging?</b>", defaultValue: true
		}
      	display()
	} 
}


def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}


def updated() {
	log.debug "Updated with settings: ${settings}"
	unschedule()
	unsubscribe()
	schedule("0 0 14 ? * FRI *", updateCheck)
//	if (debugOutput) runIn(1800,logsOff)
	initialize()
}


def initialize() {
	log.info "There are ${childApps.size()} child smartapps"
	childApps.each {child ->
		child.setDebug(debugOutput, descTextEnable)
		log.info "Child app: ${child.label}"
	}
}


def logsOff() {
    log.warn "debug logging disabled..."
    app?.updateSetting("debugOutput",[value:"false",type:"bool"])
}


def display() {
	updateCheck() 
	section{
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


def updateCheckHandler(resp, data) {

	state.InternalName = "BLMparent"
	
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
			      state.Status = "<b>** This Driver is no longer supported by ${respUD.author}  **</b>"       
			      log.warn "** This Driver is no longer supported by ${respUD.author} **"      
				break
			case { it > currentVer}:
			      state.Status = "<b>New Version Available (Version: ${respUD.driver.(state.InternalName).ver})</b>"
			      log.warn "** There is a newer version of this Driver available  (Version: ${respUD.driver.(state.InternalName).ver}) **"
			      log.warn "** $state.UpdateInfo **"
				break
			case { it < currentVer}:
			      state.Status = "<b>You are using a Test version of this Driver (Expecting: ${respUD.driver.(state.InternalName).ver})</b>"
				break
			default:
				state.Status = "Current"
				if (descTextEnable) log.info "You are using the current version of this driver"
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