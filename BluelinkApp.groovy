/**
 *  Hyundai Bluelink Application
 *
 *  Author: 		Tim Yuhl
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 *  files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 *  modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 *  Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 *  WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 *  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *  History:
 *  8/14/21 - Initial work.
 *
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

static String appVersion()   { return "1.0.0" }
def setVersion(){
	state.name = "Hyundai Bluelink Application"
	state.version = "1.0.0"
}

@Field static String global_apiURL = "https://api.telematics.hyundaiusa.com"
@Field static String client_id = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920"
@Field static String client_secret = "v558o935-6nne-423i-baa8"

definition(
		name: "Hyundai Bluelink App",
		namespace: "tyuhl",
		author: "Tim Yuhl",
		description: "Application for Hyundai Bluelink web service access.",
		importUrl:"",
		category: "Convenience",
		iconUrl: "",
		iconX2Url: ""
)

preferences {
	page(name: "mainPage")
	page(name: "debugPage", title: "Debug Options", install: false)
}

def mainPage()
{
	dynamicPage(name: "mainPage", title: "Hyundai Bluelink App", install: true, uninstall: true) {
		section(getFormat("header-blue-grad","About")) {
			paragraph "This application and the corresponding driver are used to access the Hyundai Bluelink web services"
		}
		section(getFormat("header-blue-grad","Username")) {
			input name: "user_name", type: "string", title: "Bluelink Username"
		}
		section(getFormat("header-blue-grad", "Password")) {
			input name: "user_pwd",type: "string", title: "Bluelink Password"
		}
		section(getFormat("header-blue-grad", "PIN")) {
			input name: "bluelink_pin",type: "string", title: "Bluelink PIN"
		}
		section("Logging") {
			input name: "logging", type: "enum", title: "Log Level", description: "Debug logging", required: false, defaultValue: "INFO", options: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
		}
		getDebugLink()
	}
}

////////
// Debug Stuff
///////
def getDebugLink() {
	section{
		href(
				name       : 'debugHref',
				title      : 'Debug buttons',
				page       : 'debugPage',
				description: 'Access debug buttons (authorize, refresh token, etc.)'
		)
	}
}

def debugPage() {
	dynamicPage(name:"debugPage", title: "Debug", install: false, uninstall: false) {
		section {
			paragraph "Debug buttons"
		}
		section {
			input 'authorize', 'button', title: 'authorize', submitOnChange: true
		}
		section {
			input 'refreshToken', 'button', title: 'Force Token Refresh', submitOnChange: true
		}
		section {
			input 'initialize', 'button', title: 'initialize', submitOnChange: true
		}
		section {
			input 'getStatus', 'button', title: 'Get Vehicle Status', submitOnChange: true
		}
	}
}

def appButtonHandler(btn) {
	switch (btn) {
		case 'authorize':
			authorize();
			break;
		case 'refreshToken':
			refreshToken()
			break
		case 'initialize':
			initialize()
			break
		case 'getStatus':
			getStatus()
			break
		default:
			log("Invalid Button In Handler", "error")
	}
}

void installed() {
	log("Installed with settings: ${settings}", "trace")
	initialize()
}

void updated() {
	log("Updatedwith settings: ${settings}", "trace")
	initialize()
}

void initialize() {
	setVersion()
	unschedule()
//	refreshToken()
}

void authorize() {
	log("authorize called", "trace")

	def headers = [
			"client_id": client_id,
			"client_secret": client_secret
	]
	def body = [
			"username": user_name,
			"password": user_pwd
	]
	def params = [uri: global_apiURL, path: "/v2/ac/oauth/token", headers: headers, body: body]

	try
	{
		httpPost(params) { response -> authResponse(response) }
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		log("Login failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
	}
}

void refreshToken() {
	log("refreshToken called", "trace")

	if (state.refresh_token != null)
	{
		def headers = [
				"client_id": client_id,
				"client_secret": client_secret
		]
		def body = [
				refresh_token: state.refresh_token
		]
		def params = [uri: global_apiURL, path: "/v2/ac/oauth/token/refresh", headers: headers, body: body]

		try
		{
			httpPost(params) { response -> authResponse(response) }
		}
		catch (groovyx.net.http.HttpResponseException e)
		{
			log("Login failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
		}
	}
	else
	{
		log("Failed to refresh token, refresh token null.", "error")
	}
}

def authResponse(response)
{
	log("authResponse called", "debug")

	def reCode = response.getStatus()
	def reJson = response.getData()
	log("reCode: {$reCode}", "debug")
	log("reJson: {$reJson}", "debug")

	if (reCode == 200)
	{
		state.access_token = reJson.access_token
		state.refresh_token = reJson.refresh_token

		Integer expireTime = (Integer.parseInt(reJson.expires_in) - 100)
		log("Bluelink token refreshed successfully, Next Scheduled in: ${expireTime} sec", "info")
		runIn(expireTime, refreshToken)
	}
	else
	{
		log("LoginResponse Failed HTTP Request Status: ${reCode}", "error")
	}
}

def getStatus()
{
	log("getStatus called", "trace")

	def uri = global_apiURL + "/ac/v2/enrollment/details/" + user_name
	def headers = [ access_token: state.access_token, client_id: client_id, includeNonConnectedVehicles : "Y"]
	def params = [ uri: uri, headers: headers ]
	log("getStatus ${params}", "debug")

	//add error checking
	def reJson =''
	try
	{
		httpGet(params) { response ->
			def reCode = response.getStatus();
			reJson = response.getData();
			log("reCode: ${reCode}", "debug")
			log("reJson: ${reJson}", "debug")
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		log("getStatus failed -- ${e.getLocalizedMessage()}: ${e.response.data}", "error")
		return;
	}

	//TODO: Parse the returned Json to get interesting info
}

///
// Supporting helpers
///
private determineLogLevel(data) {
	switch (data?.toUpperCase()) {
		case "TRACE":
			return 0
			break
		case "DEBUG":
			return 1
			break
		case "INFO":
			return 2
			break
		case "WARN":
			return 3
			break
		case "ERROR":
			return 4
			break
		default:
			return 1
	}
}

def log(Object data, String type) {
	data = "-- ${app.label} -- ${data ?: ''}"

	if (determineLogLevel(type) >= determineLogLevel(settings?.logging ?: "INFO")) {
		switch (type?.toUpperCase()) {
			case "TRACE":
				log.trace "${data}"
				break
			case "DEBUG":
				log.debug "${data}"
				break
			case "INFO":
				log.info "${data}"
				break
			case "WARN":
				log.warn "${data}"
				break
			case "ERROR":
				log.error "${data}"
				break
			default:
				log.error("-- ${device.label} -- Invalid Log Setting")
		}
	}
}

// concept stolen bptworld, who stole from @Stephack Code
def getFormat(type, myText="") {
	if(type == "header-green") return "<div style='color:#ffffff; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background-color:#81BC00; border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
	if(type == "header-light-grey") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background-color:#D8D8D8; border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
	if(type == "header-blue-grad") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background: linear-gradient(to bottom, #d4e4ef 0%,#86aecc 100%);  border: 2px'>${myText}</div>"
	if(type == "item-light-grey") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; font-weight: normal; padding-left: 10px; background-color:#D8D8D8; border: 1px solid'>${myText}</div>"
	if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}

