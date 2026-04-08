
                                                              README
                               Rapid Connect Platform Integration Development Toolkit Edition [C#]
                                                           Last Modified 02/03/2026
                                                           
The code sample demonstrates the process of integration of any POS [Point Of Sale] software to the Rapid Connect system 
in the C# language.  It is assumed that developers already received the GMF schema, merchant account( MID, TID, DID, Group ID), test cards and 
connectivity information as part of the development package. If not, please contact your support representative to request 
that information.

1. This code sample contains a working C# project, developed using Visual Studio 2019 and compiled using DOT NET Framework 4.8.
2. Navigate inside the folder RCToolkitSample_Csharp and open the Visual Studio solution file RCToolkitSample_Csharp.sln. 
3. Rebuild the solution in Visual Studio and run it. The project should run as a Console Application.
4. This solution contains a sample console project RCToolkitSample_Csharp which demonstrates the process of integration with Rapid Connect system with
sample Credit and Debit Sale transactions using all three supported transportation methods - XML Post over HTTP, SOAP and TCP/IP.
5. The sample project contains the following major items -
	a. Program.cs - Code file containing the Main() method and is the entry point of the application.
	b. CreditSaleRequest.cs - code file illustrates the generation of a sample test Credit Sale request in XML format 
	c. DebitSaleRequest.cs - code file illustrates the generation of a sample test Debit Sale request in XML format
	d. TestConst.cs - Contains setup parameters that should be provided by your support representative
	e. GMFSchema.cs - This file was auto-generated from the Rapid Connect GMF Schema using XSD.exe tool. This file contains the GMF schema proxy classes
 	used in the project.
    Besides the above there are other dependent code files and classes, used throughout the project.
6. The classes files used in ToolKit are only for testing purpose please make sure to regenerate the classes based on latest schema.

For information on required fields for various GMF transactions please refer to the latest GMF Transactions Specification.

Once the transaction request in XML format is ready, it is required to send it to Rapid Connect Transaction Processing Service 
Host. This can be achieved either using HTTPS or SOAP or TCP/IP protocol. 

=============================================================================================================================================
PLEASE MAKE SURE THAT BEFORE YOU RUN THE SAMPLE CODE, YOU REPLACE THE DUMMY VALUES (MID, TID, DID, IP ADDRESS, PORT ETC.) WITH THE ACTUAL
VALUES PROVIDED BY FISERV FOR TESTING WITH RAPID CONNECT. IF YOU TRY RUNNING THE SAMPLE CODE AS IT IS YOU MIGHT GET UNEXPECTED RESPONSES.
=============================================================================================================================================



                             =======================================================================
                                       Integrate Applications with XML over HTTPS Protocol
                             =======================================================================
	
1. Go through the HttpPostHandler.cs class to implement the integration using your assigned credentials.  Please refer to
   Secure Transport Reference Guide for more information.
3. Make sure that the test Rapid connect Service URL [for example https://stg.dw.us.fdcnet.biz/rc] is accessible from the 
   POS [Point of Sale] system where integration is being made.
4. Program.cs file describes sending the GMF transaction request in XML format and receiving response from RC system 
   using HTTP protocol.

                             =======================================================================
                                            Integrate Applications with SOAP Protocol
                             =======================================================================
	
The complete Rapid Connect WSDL for the Secure Transport integration is available at https://stg.dw.us.fdcnet.biz/rc.wsdl

1. Go through the SoapHandler.cs class to implement the integration using your assigned credentials.  Please refer to
   Secure Transport Reference Guide for more information.
2. Make sure that the test Rapid connect Service URL [for example https://stg.dw.us.fdcnet.biz/rc] is accessible from the 
   POS [Point of Sale] system where integration is being made.
3. Program.cs file describes sending the GMF transaction request in XML format and receiving response from RC system using 
   SOAP protocol.

			     =======================================================================
                                            Integrate Applications with TCP/IP Protocol
                             =======================================================================
	
1. Go through the TCPIPHandler.cs class to implement the integration using your assigned credentials.  Please refer to
   RAPID CONNECT TCP IP INTERFACE SPEC for more information.
2. Make sure that connectivity to the Rapid connect Service using TCP/IP is successful from the POS [Point of Sale] system where integration is being made.
3. Program.cs file describes sending the GMF transaction request in XML format and receiving response from RC system using 
   TCP/IP protocol.

	
                             -----------------------------------------------------------------------
		
