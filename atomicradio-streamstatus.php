<?php
// Seeing as Java lately has been throwing SSL errors for the Dubtrack API, this is a PHP script to pull the SSL API
// pages and spit out JSON. This file is hosted on my web hosting (jsaunders.id.au/atomicradio-streamstatus.php)
// but this code is provided to show that I'm not stealing anything from you. You are more than welcome to host this
// file yourself on your webserver (requires php_curl) and change the config to reflect the new address.

// What is the script calling for?
if (isset($_GET["check"])) {
    if ($_GET["check"] == "getStatus") {
        // Script calling for Stream Status
        if (isset($_GET["roomName"])) {
        $roomName = $_GET["roomName"];
        $url = "https://api.dubtrack.fm/room/" . $roomName;
        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_HTTPHEADER, array('Content-Type: application/json'));
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        $curl_response = curl_exec($ch);
        print_r($curl_response);
        curl_close($ch);
        } else {
            echo("ERROR: No Room Name specified!");
        }
    } else if ($_GET["check"] == "getUserID") {
        // Script is calling for the User ID of the current DJ
        if (isset($_GET["roomID"])) {
        $roomID = $_GET["roomID"];
        $url = "https://api.dubtrack.fm/room/" . $roomID . "/playlist/active";
        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_HTTPHEADER, array('Content-Type: application/json'));
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        $curl_response = curl_exec($ch);
        print_r($curl_response);
        curl_close($ch);
        } else {
            echo("ERROR: No Room ID specified!");
        }
    } else if ($_GET["check"] == "getUserName") {
        // Script is calling for the Username of the current DJ
        if (isset($_GET["userID"])) {
        $userID = $_GET["userID"];
        $url = "https://api.dubtrack.fm/user/" . $userID;
        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_HTTPHEADER, array('Content-Type: application/json'));
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        $curl_response = curl_exec($ch);
        print_r($curl_response);
        curl_close($ch);
        } else {
            echo("ERROR: No User ID specified!");
        }
    } else {
        echo("ERROR: Malformed request!");
    }
} else {
    echo("ERROR: Malformed request!");
}
?>
