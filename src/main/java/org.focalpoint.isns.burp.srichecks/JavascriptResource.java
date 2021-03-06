/**
 * BurpSuite JavaScript Security Extension
 * Copyright (C) 2019  Focal Point Data Risk, LLC
 * Written by: Peter Hefley
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General 
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the 
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for 
 * more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program.  
 * If not, see <https://www.gnu.org/licenses/>.
 */
package org.focalpoint.isns.burp.srichecks;

import java.util.HashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.focalpoint.isns.burp.srichecks.Requester;

import burp.IBurpExtenderCallbacks;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.focalpoint.isns.burp.srichecks.DNSResolver;

public class JavascriptResource {
    private String src;
    private String originalTag;
    private Element parsedTag;
    private String data = "";
    private byte[] binaryData = null;
    private Boolean dnsValid = false;
    private IBurpExtenderCallbacks callbacks = null;
    public static final String NO_DATA_RECEIVED = "NO DATA NO DATA NO DATA";
    private HashMap<String,String> hashes = new HashMap<String,String>();

    /**
     * Default constructor
     */
    public JavascriptResource(){}

    /**
     * Constructor to use when you have all of the necessary items
     * @param  callbacks  The burp suite callbacks object, needed to use the HTTP interface
     * @param  srcString  The SRC attribute, or source, of the JavaScript resource
     * @param  tagString  A string of the HTML tag which was used to reference the JavaScript
     */
    public JavascriptResource(IBurpExtenderCallbacks callbacks, String srcString, String tagString){
        setSrc(srcString);
        setCallbacks(callbacks);
        setOriginalTag(tagString);
        getResource();
        calculateHashes();
    }

    /**
     * Set the source, or SRC attribute, of the object
     * @param newSrc A string containing the value of the SRC attribute for a JavaScript resource
     */
    public void setSrc(String newSrc){
        src = newSrc;
    }

    /**
     * Get the SRC for this object
     * @return a String of the SRC attribute for this JavaScript resource
     */
    public String getSrc(){
        return src;
    }

    /**
     * Set the callbacks to use for this object
     * @param cb IBurpExtenderCallbacks object to use for burp callbacks
     */
    public void setCallbacks(IBurpExtenderCallbacks cb){
        callbacks = cb;
    }

    /**
     * Get the callbacks used by this object
     * @return IBurpExtenderCallbacks object used by this object for burp interface
     */
    public IBurpExtenderCallbacks getCallbacks(){
        return callbacks;
    }

    /**
     * Set the original tag on this object, e.g., <script src="someurl"></script>
     * @param ot the string of the original HTML tag
     */
    public void setOriginalTag(String ot){
        originalTag = ot;
        parseTag();
    }

    /**
     * Get the original HTML tag
     * @return a string of the original HTML tag for this resource
     */
    public String getOriginalTag(){
        return originalTag;
    }

    /**
     * Parse the HTML tag that we have in to it's disparate parts, stored as a separate object.
     * Obtained using getParsedTag
     */
    public void parseTag(){
        Document doc = Jsoup.parse(originalTag);
        parsedTag = doc.getElementsByTag("script").first();
    }

    /** 
     * Get the parsedTag object
     * @return a jsoup Element object which is the original tag, all parsed out
     */
    public Element getParsedTag(){
        return parsedTag;
    }

    /**
     * Actually go and get the referenced JavaScript resource via HTTP through burp
     */
    public void getResource(){
        URI thisUri = URI.create(src);
        DNSResolver myResolver = new DNSResolver();
        dnsValid = myResolver.hasValidRecordsForAUrl(thisUri.getHost());
        if (dnsValid){
            try {
                /* 
                * There is a chance at this point that callbacks is null, that's okay
                * that is the way it should be for testing without going through burp
                */
                Requester myRequester = new Requester(callbacks, src);
                data = myRequester.getResponseBody();
                binaryData = myRequester.getResponseBodyBytes();
                dnsValid = !(myResolver.hasBadCnames(thisUri.getHost())); 
                // look, if we were able to get the resource, as long as it has no bad cnames, we're good
                dnsValid = true;
            }
            catch (Exception ex) {
                data = NO_DATA_RECEIVED;
                System.err.println("[JS-SRI][-] There was an issue getting the JavaScript file at " + src);
                dnsValid = myResolver.hasValidRecordsForAUrl(thisUri.getHost());
                if (!(dnsValid)){
                    System.err.println("[JS-SRI][-] There was an issue getting the JavaScript file at " + src + ". DNS was not valid for " + thisUri.getHost() + ".");
                }
            }
        } else {
            System.err.println("[JS-SRI][-] There was an issue getting the JavaScript file at " + src + ". DNS was not valid for " + thisUri.getHost() + ".");
            data = NO_DATA_RECEIVED;
        }
    }

    /**
     * Does this resource have any data (the actual JavaScript file) that has been retrieved?
     * @return true if there is data present, false if not
     */
    public boolean hasData(){
        return (!data.equals(NO_DATA_RECEIVED));
    }

    /**
     * Get the data for this resource
     * @return the string of the data
     */
    public String getData(){
        return data;
    }

    /**
     * Determine if the FQDN for the source URL could be looked up
     * @return true if the DNS hostname could be looked up, false if not
     */
    public boolean hasValidHostname(){
        return dnsValid;
    }

    /**
     * Hash the data we have and store the hashes
     * @param  algorithm  the Java MessageDigest algorithm to use to generate the hash
     * @return            a base64 encoded representation of the hash value
     */
    private String dataHasher(String algorithm) {
        if (hasData()){
            try {
                MessageDigest digest = MessageDigest.getInstance(algorithm);
                byte[] encodedHash = digest.digest(binaryData);
                return Base64.getEncoder().encodeToString(encodedHash);
            }
            catch (NoSuchAlgorithmException ex) {
                System.err.println("[-] The provided algorithm string (" + algorithm + ") is not valid.");
                return "";
            }
        }
        return "";
    }

    /**
     * Calculate all of the hashes for all valid algorithms for this item
     */
    public void calculateHashes(){
        if (hasData()){
            hashes.put("sha256",dataHasher("SHA-256"));
            hashes.put("sha384",dataHasher("SHA-384"));
            hashes.put("sha512",dataHasher("SHA-512"));
            hashes.put("md5",dataHasher("MD5"));
            hashes.put("sha1",dataHasher("SHA-1"));
        }
    }

    /**
     * Get the hashes for this object
     * @return a hashmap keyed by algorithm of all base64 encoded hashes for this object's data
     */
    public HashMap<String,String> getHashes(){
        return hashes;
    }

    /** 
     * Check to see if a given algorithm/hash value pair is a match for this object
     * @param  hashValue  the base64 encoded hash value to chec
     * @param  algorithm  the string name of the algorithm for this hash
     * @return            true if the given algorithm/hash value pair is a match for this resource
     */
    public Boolean checkHash(String hashValue, String algorithm){
        if (hashes.keySet().contains(algorithm))
        {
            return (hashes.get(algorithm).equals(hashValue));
        }
        else {
            return false;
        }
    }

    /**
     * Get the integrity attribute from the HTML tag
     * @return a string of the integrity attribute, null if there is none
     */
    public String getIntegrityAttribute(){
        if (parsedTag.hasAttr("integrity")) {
            return parsedTag.attr("integrity");
        } else {
            return null;
        }
    }

    /**
     * Check the SRI integrity of a javascript tag
     * @return true if the integrity attribute is correct, false otherwise
     */
    public Boolean checkIntegrity(){
        if (parsedTag.hasAttr("integrity")) {
            String integrityAttribute = getIntegrityAttribute();
            String algorithm = integrityAttribute.substring(0, integrityAttribute.indexOf("-"));
            String hashToCheck = integrityAttribute.substring(integrityAttribute.indexOf("-")+1);
            return checkHash(hashToCheck, algorithm);
        } else {
            return false;
        }
    }
}
