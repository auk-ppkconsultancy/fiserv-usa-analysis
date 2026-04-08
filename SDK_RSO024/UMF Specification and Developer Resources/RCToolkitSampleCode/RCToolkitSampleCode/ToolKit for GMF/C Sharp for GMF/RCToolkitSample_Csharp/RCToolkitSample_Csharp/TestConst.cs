using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace GlobalMessageFormatter
{
    class TestConst
    {
		// The pamameters below should be provided by your support representative
		
        // Parameters for transaction request
        public const string REQUEST_TPPID = "XX1234";  // 
        public const string REQUEST_TERMID = "12345678";
        public const string REQUEST_MERCHID = "XX1234";
        public const string REQUEST_GROUPID = "XXX01";
        public const string REQUEST_DEBIT_TRACK2 = "4017779995555556=30041200000000001";
        public const string REQUEST_DEBIT_PINDATA = "99A14CA1B65D821B";
        public const string REQUEST_DEBIT_KEYSERIALNUMDATA = "F8765432100015200578";
 
        // Parameters for TCPIP protocol
        public const string TCP_HOST = "XX.XXX.XX.X";
        public const int TCP_PORT = 1234;

        // Parameters for HTTP protocol
         public const string HTTP_AUTHID = "XXXXX0000000000|00000000";

        public const string HTTP_DID = "00000000000000000000";
   }
}
