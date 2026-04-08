				
                                                              README
                              Rapid Connect Platform Integration Development Toolkit Edition [PHP]
                                                           Last Modified 02/03/2026
                                                           
The code sample demonstrates the process of integration of any POS [Point Of Sale] software to the Rapid Connect system 
developed in the PHP language. It is assumed that developers already received the GMF schema, merchant account( MID, TID, DID), test cards and 
connectivity information as part of the development package. If not, please contact your support representative to request 
that information.

1. This code sample contains a working PHP project, developed in PHP open source IDE and compiled using PHP version 7.3. The project also uses the 
   following PHP open source solutions:
   a. PHP PEAR Library was used to serialize the GMF Message object to formulate GMF XML request.
   b. PHP CURL Library was used to establish connections to Datawire using HTTPS POST method.
   You should download and install the above libraries in order to run this project successfully.
3. Once the above dependant libraries are installed, open your PHP IDE and create a new project. Import all the supplied project files in your new project. 
   Rebuild the project and run it. The project should run as a console application.
4. The sample project contains the following major items - 
	a. Credit_Sale_HTTP.php - illustrates the generation of a sample test Credit Sale request in XML format and its transportation using XML over 
           HTTP Post protocol. 
	b. Credit_Sale_SOAP.php - illustrates the generation of a sample test Credit Sale request in XML format and its transportation using SOAP protocol. 
	c. Credit_Sale_TCP.php - illustrates the generation of a sample test Credit Sale request in XML format and its transportation using TCP/IP protocol. 
	d. Debit_Sale_HTTP.php - illustrates the generation of a sample test Debit Sale request in XML format and its transportation using XML over 
           HTTP Post protocol. 
	e. Debit_Sale_SOAP.php - illustrates the generation of a sample test Debit Sale request in XML format and its transportation using SOAP protocol. 
	f. Debit_Sale_TCP.php - illustrates the generation of a sample test Debit Sale request in XML format and its transportation using TCP/IP protocol. 
	g. PHP_GMF_Lib.php - This code file was auto-generated from the Rapid Connect GMF Schema using PHP open source tool. This file contains all the 
           GMF proxy data classes used for creating the different GMF classes.
6. The classes files used in ToolKit are only for testing purpose please make sure to regenerate the classes based on latest schema.
	
For information on required fields for various GMF transactions please refer to the latest GMF Transactions Specification.

Once the transaction request in XML format is ready, it is required to send it to Rapid Connect Transaction Processing Service 
Host. This can be achieved either using XML over HTTPS or SOAP or TCP/IP protocol.


                             =======================================================================
                                       Integrate Applications with XML over HTTPS Protocol
                             =======================================================================
	
1. The following Rapid Connect credentials should be used for connectivity using this protocol - (Masked values given here)
   	App ID = XXXXXXXXXXXXXXX
   	Auth ID = XXXXXXXXXXXXXXX|XXXXXXXX	Format of Auth ID is [RC MID]|[RC TID]
   	DID =	XXXXXXXXXXXXXXXXXXXX
   	Service ID = XXX
   	Client Ref = XXXXXXXX|XXXXXX	Format of Client Ref is 'XX'[6 digit STAN]|[6 digit TPP ID]

2. Go through the Credit_Sale_HTTP.php file to implement the integration using your assigned credentials.  Please refer to
   Secure Transport Reference Guide for more information.
3. Make sure that the test Rapid connect Service URL [for example https://stg.dw.us.fdcnet.biz/rc] is accessible from the 
   POS [Point of Sale] system where integration is being done.
4. Credit_Sale_HTTP.php file describes sending the GMF transaction request in XML format and receiving response from RC system 
   using HTTP protocol.

			     =======================================================================
                                            Integrate Applications with SOAP Protocol
                             =======================================================================

The complete Rapid Connect WSDL for the Secure Transport integration is available at https://stg.dw.us.fdcnet.biz/rc.wsdl

1. The following Rapid Connect credentials should be used for connectivity using this protocol - (Masked values given here)
   	App ID = XXXXXXXXXXXXXXX
   	Auth ID = XXXXXXXXXXXXXXX|XXXXXXXX	Format of Auth ID is [RC MID]|[RC TID]
   	DID =	XXXXXXXXXXXXXXXXXXXX
   	Service ID = XXX
   	Client Ref = XXXXXXXX|XXXXXX	Format of Client Ref is 'XX'[6 digit STAN]|[6 digit TPP ID]

2. Go through the Credit_Sale_SOAP.php file to implement the integration using your assigned credentials.  Please refer to
   Secure Transport Reference Guide for more information.
3. Make sure that the test Rapid connect Service URL [for example https://stg.dw.us.fdcnet.biz/rc] is accessible from the 
   POS [Point of Sale] system where integration is being made.
4. Credit_Sale_SOAP.php file describes sending the GMF transaction request in XML format and receiving response from RC system 
   using SOAP protocol.


