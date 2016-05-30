# AtomicRadio
##Introduction
Does your minecraft server have a radio stream but you're having trouble promoting it to your players?

For months I was manually typing out broadcasts to say which song was playing, which DJ was on, the listen URL, etc.

I've come across other plugins that can show the status of a ShoutCast stream but none of them really fit, so I've decided to write one myself.

##Commands
/radio - Checks the status URL for the radio station (defined in the config) and tells you if it is online, how many listeners are connected, what
song is playing and the listen URL (defined in the config).

/radio dj - Returns the current DJ (if any).

/radio request/req <text> - Requests a song from the current DJ.

/radio reqlist - Returns the first 5 requests.

/radio reqlist <num> - Returns page <num> of requests.

###Admin/Current DJ only:
/radio dj on - Assigns the user issuing the command DJ status. Cannot be used if there is a DJ currently.

/radio dj off - Removes that user's DJ status. (Forcefully if admin)

/radio bc/broadcast - Broadcasts the currently playing song, which DJ is playing and the listen URL

/radio reqlist del <num/all> - Deletes request <num> or all requests.

##Config Options
-Listen URL (Usually http://server:port/listen?sid=streamnumber)

-Status URL (Usually http://server:port/stats?sid=streamnumber)

-DJ Prefix (i.e. "DJ")

-Message Prefix (i.e. "AtomicRadio")

##Permission nodes
atomicradio.use - Allows use of /radio dj on, /radio dj off, /radio bc/broadcast, /radio reqlist del

atomicradio.admin - Allows use of the above when not the DJ

##Installation
Copy AtomicRadio.jar to your Plugins folder, start your server (it will create a default config), edit config to your liking and then type /radio reload in console.
