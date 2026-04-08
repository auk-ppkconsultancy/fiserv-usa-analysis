 <?php
error_reporting(1);

// Last Modified: 02/03/2026
// The below code will prepare Debit Sale transaction request object populating various
// transaction parameters. The parameter values used below are test data and should not used for
// actual real-time authorization.

//include files
include("Serializer.php"); //PEAR XML serialization include file
include("Unserializer.php"); //PEAR XML de-serialization include files
include("PHP_GMF_Lib.php"); //GMF Data classes
require_once('lib/nusoap.php'); //include nusoap library (SOAP connectivity to Datawire)

//*** Create Debit Sale Request ***//
$obj_GMFMessageVariants = CreateDebitSaleRequest();

//*** Generate Client Ref Number in the format <STAN>|<TPPID>, right justified and left padded with "0" ***/
$clientRef = GenerateClientRef($obj_GMFMessageVariants);

//*** Serialize the GMF object to XML Payload ***//
$result = SerializeToXMLString($obj_GMFMessageVariants);

//**** Display XML Request Payload in console ***//
echo "GMF Request Payload: \n";
echo $result;
echo "\n";

//*** Send GMF XML Payload to Datawire using SOAP protocol ***//
$TxnResponse = SendMessage($result, $clientRef);

//*** Display XML Response Payload in console ***//
echo "GMF Response Payload: \n";
echo $TxnResponse;
echo "\n";
/*--------------------------------------*/


//Create Debit Sale Request Object
function CreateDebitSaleRequest()
{
	/* Based on the GMF Specification, fields that are mandatory or related to
    this transaction should be populated.*/

	//GMF - create object for GMFMessageVariants
	$obj_GMFMessageVariants = new GMFMessageVariants();

	//Debit Request - create object for DebitRequestDetails
	$obj_DebitRequestDetails = new DebitRequestDetails();

	//Common Group - create object for CommonGrp
	$obj_CommonGrp = new CommonGrp();

	//populate common transaction fields
	$obj_CommonGrp -> setPymtType("Debit");	//Payment Type = Debit
	$obj_CommonGrp -> setTxnType("Sale");	//Transaction Type = Sale
	$obj_CommonGrp -> setLocalDateTime("20200114042651");	//Local Txn Date-Time
	$obj_CommonGrp -> setTrnmsnDateTime("20200114042651");	//Local Transmission Date-Time

	$obj_CommonGrp -> setSTAN("100027");	//System Trace Audit Number
	$obj_CommonGrp -> setRefNum("112233455625");	//Reference Number
	$obj_CommonGrp -> setOrderNum("555566678914"); //Order Number
	$obj_CommonGrp -> setTPPID("XXXXXX");	//TPP ID

	$obj_CommonGrp -> setTermID("XXXXXXXX");	//Terminal ID

	$obj_CommonGrp -> setMerchID("XXXXXXXXXXXXXXX");	//Merchant ID

	$obj_CommonGrp -> setPOSEntryMode("901");	//Entry Mode for the transaction
	$obj_CommonGrp -> setPOSCondCode("00");		// POS Cond Code = 00-Normal Presentment
	$obj_CommonGrp -> setTermCatCode("01");		// Terminal Category Code = 01-POS
	$obj_CommonGrp -> setTermEntryCapablt("04");	// Terminal Entry Capability
	$obj_CommonGrp -> setTxnAmt("000000002400");	//Transaction Amount = $24.00
	$obj_CommonGrp -> setTxnCrncy("840");	// Transaction Currency = 840-US Country Code
	$obj_CommonGrp -> setTermLocInd("0");	// Location Indicator for the POS
	$obj_CommonGrp -> setCardCaptCap("1");	// Card capture capibility for the terminal
	$obj_CommonGrp -> setGroupID("XXX01");	//Group ID
	//add CommonGrp to DebitRequestDetails object
	$obj_DebitRequestDetails -> setCommonGrp($obj_CommonGrp);

	//Card Group - create object for CardGrp
	$obj_CardGrp = new CardGrp();
	$obj_CardGrp -> setTrack2Data("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");	//Track 2 Data for Debit transaction

	//add CardGrp to DebitRequestDetails object
	$obj_DebitRequestDetails -> setCardGrp($obj_CardGrp);

	//PIN Data Group - create object for PINGrp
	$obj_PINGrp = new PINGrp();
	$obj_PINGrp -> setPINData("XXXXXXXXXXXXXXXX");	//Debit card PIN data
	$obj_PINGrp -> setKeySerialNumData("XXXXXXXXXXXXXXXXXXXX");	//Key Serial Number for the transaction

	//add PINGrp to DebitRequestDetails object
	$obj_DebitRequestDetails -> setPINGrp($obj_PINGrp);

	//Additional Amount Group
	$obj_AddlAmtGrp = new AddtlAmtGrp();
	$obj_AddlAmtGrp -> setPartAuthrztnApprvlCapablt("1"); //Partial Authorization capibility
	//add AddlAmtGrp to DebitRequestDetails object
	$obj_DebitRequestDetails -> setAddtlAmtGrp($obj_AddlAmtGrp);

	//assign DebitRequest to the GMF object
	$obj_GMFMessageVariants -> setDebitRequest($obj_DebitRequestDetails);

	return $obj_GMFMessageVariants;
}

//Generate Client Ref Number in the format <STAN>|<TPPID>, right justified and left padded with "0"
function GenerateClientRef (GMFMessageVariants $gmfMesssageObj)
{
	$strSTAN = $gmfMesssageObj -> getDebitRequest() -> getCommonGrp() -> getSTAN();
	$strTPP = $gmfMesssageObj -> getDebitRequest() -> getCommonGrp() -> getTPPID();
	$clientRef = '00'.$strSTAN.'|'.$strTPP;

	return $clientRef;
}

//Serialize object to XML Payload
function SerializeToXMLString(GMFMessageVariants $gmfMesssageObj)
{
	// create XML serializer instance using PEAR
	$serializer = new XML_Serializer(array("indent" => ""));

	$serializer->setOption("rootAttributes", array("xmlns" => "com/fiserv/Merchant/gmfV10.02"));

	// perform serialization
	$result = $serializer->serialize($gmfMesssageObj);

	// check result code and return XML Payload
	if($result == true)
		return str_replace("GMFMessageVariants", "GMF", $serializer->getSerializedData());
	else
		return "Serizalion Failed";
}

//Send Transaction to Datawire
function SendMessage($gmfXMLPayload, $clientRef)
{
	//create SOAP message with xml transaction payload
	$requestData = generateRequest('00010774080220390428', 'RAPIDCONNECTVXN', 'RCTST0000000001|00000002', $clientRef, '160', $gmfXMLPayload);

	//instantiate PHP NU-SOAP client
	$client = new nusoap_client('https://stg.dw.us.fdcnet.biz/rc',false);
	$client->soap_defencoding = 'utf-8';
	$client->useHTTPPersistentConnection();
	$soapaction = "http://securetransport.dw/rcservice";

	//send GMF SOAP message to Datawire
	$response = $client->send($requestData, $soapaction, '');

	$response_array = xmlstr_to_array($client->responseData);
	return $response_array['S:Body']['Response']['TransactionResponse']['Payload'];
}

//Generate SOAP message
function generateRequest($DID, $App, $Auth, $ClientRef, $ServiceID, $Payload){
	$request_xml = '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:soap="http://securetransport.dw/rcservice/soap">
	   <soapenv:Header/>
	   <soapenv:Body>
		  <soap:Request Version="3" ClientTimeout="?">
			 <soap:ReqClientID>
				<soap:DID>'.$DID.'</soap:DID>
				<soap:App>'.$App.'</soap:App>
				<soap:Auth>'.$Auth.'</soap:Auth>
				<soap:ClientRef>'.$ClientRef.'</soap:ClientRef>
			 </soap:ReqClientID>
			 <soap:Transaction>
				<soap:ServiceID>'.$ServiceID.'</soap:ServiceID>
				<soap:Payload Encoding="cdata"><![CDATA['.$Payload.']]></soap:Payload>
			 </soap:Transaction>
		  </soap:Request>
	   </soapenv:Body>
	</soapenv:Envelope>';

	return $request_xml;
}

//Utility function - convert XML to array
function xmlstr_to_array($xmlstr) {
  $doc = new DOMDocument();
  $doc->loadXML($xmlstr);
  return domnode_to_array($doc->documentElement);
}

//Utility function - convert XML node to array
function domnode_to_array($node) {
  $output = array();
  switch ($node->nodeType) {
   case XML_CDATA_SECTION_NODE:
   case XML_TEXT_NODE:
    $output = trim($node->textContent);
   break;
   case XML_ELEMENT_NODE:
    for ($i=0, $m=$node->childNodes->length; $i<$m; $i++) {
     $child = $node->childNodes->item($i);
     $v = domnode_to_array($child);
     if(isset($child->tagName)) {
       $t = $child->tagName;
       if(!isset($output[$t])) {
        $output[$t] = array();
       }
       $output[$t][] = $v;
     }
     elseif($v) {
      $output = (string) $v;
     }
    }
    if(is_array($output)) {
     if($node->attributes->length) {
      $a = array();
      foreach($node->attributes as $attrName => $attrNode) {
       $a[$attrName] = (string) $attrNode->value;
      }
      $output['@attributes'] = $a;
     }
     foreach ($output as $t => $v) {
      if(is_array($v) && count($v)==1 && $t!='@attributes') {
       $output[$t] = $v[0];
      }
     }
    }
   break;
  }
  return $output;
}

?>