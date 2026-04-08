<?php

class AltMerchNameAndAddrGrp{
	private $merchName;
	private $merchAddr;
	private $merchCity;
	private $merchState;
	private $merchCnty;
	private $merchPostalCode;
	private $merchCtry;

	public function setMerchName($MerchName) {
		$this->MerchName = $MerchName;
	}
	public function getMerchName() {
		return $this->MerchName;
	}

	public function setMerchAddr($MerchAddr) {
		$this->MerchAddr = $MerchAddr;
	}
	public function getMerchAddr() {
		return $this->MerchAddr;
	}

	public function setMerchCity($MerchCity) {
		$this->MerchCity = $MerchCity;
	}
	public function getMerchCity() {
		return $this->MerchCity;
	}

	public function setMerchState($MerchState) {
		$this->MerchState = $MerchState;
	}
	public function getMerchState() {
		return $this->MerchState;
	}

	public function setMerchCnty($MerchCnty) {
		$this->MerchCnty = $MerchCnty;
	}
	public function getMerchCnty() {
		return $this->MerchCnty;
	}

	public function setMerchPostalCode($MerchPostalCode) {
		$this->MerchPostalCode = $MerchPostalCode;
	}
	public function getMerchPostalCode() {
		return $this->MerchPostalCode;
	}

	public function setMerchCtry($MerchCtry) {
		$this->MerchCtry = $MerchCtry;
	}
	public function getMerchCtry() {
		return $this->MerchCtry;
	}
}

class CheckGrp{
	private $mICR;
	private $checkAcctNum;
	private $drvLic;
	private $stateCode;
	private $dLDateOfBirth;
	private $chkSvcPvdr;
	private $chkEntryMethod;

	public function setMICR($MICR) {
		$this->MICR = $MICR;
	}
	public function getMICR() {
		return $this->MICR;
	}

	public function setCheckAcctNum($CheckAcctNum) {
		$this->CheckAcctNum = $CheckAcctNum;
	}
	public function getCheckAcctNum() {
		return $this->CheckAcctNum;
	}

	public function setDrvLic($DrvLic) {
			$this->DrvLic = $DrvLic;
	}
	public function getDrvLic() {
		return $this->DrvLic;
	}

	public function setStateCode($StateCode) {
		$this->StateCode = $StateCode;
	}
	public function getStateCode() {
			return $this->StateCode;
	}

	public function setDLDateOfBirth($DLDateOfBirth) {
		$this->DLDateOfBirth = $DLDateOfBirth;
	}
	public function getDLDateOfBirth() {
		return $this->DLDateOfBirth;
	}

	public function setChkSvcPvdr($ChkSvcPvdr) {
		$this->ChkSvcPvdr = $ChkSvcPvdr;
	}
	public function getChkSvcPvdr() {
		return $this->ChkSvcPvdr;
	}

	public function setChkEntryMethod($ChkEntryMethod) {
		$this->ChkEntryMethod = $ChkEntryMethod;
	}
	public function getChkEntryMethod() {
		return $this->ChkEntryMethod;
	}
}

class SecrTxnGrp{
	private $merchTxnID;
	private $cCAFCollectInd;
	private $secrTxnAD;
	private $cAVResultCode;

	public function setMerchTxnID($MerchTxnID) {
		$this->MerchTxnID = $MerchTxnID;
	}
	public function getMerchTxnID() {
		return $this->MerchTxnID;
	}

	public function setUCAFCollectInd($UCAFCollectInd) {
		$this->UCAFCollectInd = $UCAFCollectInd;
	}
	public function getUCAFCollectInd() {
		return $this->UCAFCollectInd;
	}

	public function setSecrTxnAD($SecrTxnAD) {
		$this->SecrTxnAD = $SecrTxnAD;
	}
	public function getSecrTxnAD() {
		return $this->SecrTxnAD;
	}

	public function setCAVResultCode($CAVResultCode) {
		$this->CAVResultCode = $CAVResultCode;
	}
	public function getCAVResultCode() {
		return $this->CAVResultCode;
	}
}

class AddtlAmtGrp{
	private $addAmt;
	private $addAmtCrncy;
	private $addAmtType;
	private $addAmtAcctType;
	private $holdInfo;
	private $partAuthrztnApprvlCapablt;

	public function setAddAmt($AddAmt) {
		$this->AddAmt = $AddAmt;
	}
	public function getAddAmt() {
		return $this->AddAmt;
	}

	public function setAddAmtCrncy($AddAmtCrncy) {
		$this->AddAmtCrncy = $AddAmtCrncy;
	}
	public function getAddAmtCrncy() {
		return $this->AddAmtCrncy;
	}

	public function setAddAmtType($AddAmtType) {
		$this->AddAmtType = $AddAmtType;
	}
	public function getAddAmtType() {
		return $this->AddAmtType;
	}

	public function setAddAmtAcctType($AddAmtAcctType) {
		$this->AddAmtAcctType = $AddAmtAcctType;
	}
	public function getAddAmtAcctType() {
		return $this->AddAmtAcctType;
	}

	public function setHoldInfo($HoldInfo) {
		$this->HoldInfo = $HoldInfo;
	}
	public function getHoldInfo() {
		return $this->HoldInfo;
	}

	public function setPartAuthrztnApprvlCapablt($PartAuthrztnApprvlCapablt) {
		$this->PartAuthrztnApprvlCapablt = $PartAuthrztnApprvlCapablt;
	}
	public function getPartAuthrztnApprvlCapablt() {
		return $this->PartAuthrztnApprvlCapablt;
	}
}

class CommonGrp{
	private $pymtType;
	private $reversalInd;
	private $txnType;
	private $localDateTime;
	private $trnmsnDateTime;
	private $sTAN;
	private $refNum;
	private $orderNum;
	private $tPPID;
	private $tPPID2;
	private $termID;
	private $merchID;
	private $altMerchID;
	private $merchCatCode;
	private $pOSEntryMode;
	private $pOSCondCode;
	private $termCatCode;
	private $termEntryCapablt;
	private $txnAmt;
	private $txnCrncy;
	private $termLocInd;
	private $cardCaptCap;
	private $groupID;


	public function setPymtType($PymtType) {
		$this->PymtType = $PymtType;
	}
	public function getPymtType() {
		return $this->PymtType;
	}

	public function setReversalInd($ReversalInd) {
		$this->ReversalInd = $ReversalInd;
	}
	public function getReversalInd() {
		return $this->ReversalInd;
	}

	public function setTxnType($TxnType) {
		$this->TxnType = $TxnType;
	}
	public function getTxnType() {
		return $this->TxnType;
	}

	public function setLocalDateTime($LocalDateTime) {
		$this->LocalDateTime = $LocalDateTime;
	}
	public function getLocalDateTime() {
		return $this->LocalDateTime;
	}

	public function setTrnmsnDateTime($TrnmsnDateTime) {
		$this->TrnmsnDateTime = $TrnmsnDateTime;
	}
	public function getTrnmsnDateTime() {
		return $this->TrnmsnDateTime;
	}

	public function setSTAN($STAN) {
		$this->STAN = $STAN;
	}
	public function getSTAN() {
		return $this->STAN;
	}

	public function setRefNum($RefNum) {
		$this->RefNum = $RefNum;
	}
	public function getRefNum() {
		return $this->RefNum;
	}

	public function setOrderNum($OrderNum) {
		$this->OrderNum = $OrderNum;
	}
	public function getOrderNum() {
		return $this->OrderNum;
	}

	public function setTPPID($TPPID) {
		$this->TPPID = $TPPID;
	}
	public function getTPPID() {
		return $this->TPPID;
	}

	public function setTPPID2($TPPID2) {
		$this->TPPID2 = $TPPID2;
	}
	public function getTPPID2() {
		return $this->TPPID2;
	}

	public function setTermID($TermID) {
		$this->TermID = $TermID;
	}
	public function getTermID() {
		return $this->TermID;
	}

	public function setMerchID($MerchID) {
		$this->MerchID = $MerchID;
	}
	public function getMerchID() {
		return $this->MerchID;
	}

	public function setAltMerchID($AltMerchID) {
		$this->AltMerchID = $AltMerchID;
	}
	public function getAltMerchID() {
		return $this->AltMerchID;
	}

	public function setMerchCatCode($MerchCatCode) {
		$this->MerchCatCode = $MerchCatCode;
	}
	public function getMerchCatCode() {
		return $this->MerchCatCode;
	}

	public function setPOSEntryMode($POSEntryMode) {
		$this->POSEntryMode = $POSEntryMode;
	}
	public function getPOSEntryMode() {
		return $this->POSEntryMode;
	}

	public function setPOSCondCode($POSCondCode) {
		$this->POSCondCode = $POSCondCode;
	}
	public function getPOSCondCode() {
		return $this->POSCondCode;
	}

	public function setTermCatCode($TermCatCode) {
		$this->TermCatCode = $TermCatCode;
	}
	public function getTermCatCode() {
		return $this->TermCatCode;
	}

	public function setTermEntryCapablt($TermEntryCapablt) {
		$this->TermEntryCapablt = $TermEntryCapablt;
	}
	public function getTermEntryCapablt() {
		return $this->TermEntryCapablt;
	}

	public function setTxnAmt($TxnAmt) {
		$this->TxnAmt = $TxnAmt;
	}
	public function getTxnAmt() {
		return $this->TxnAmt;
	}

	public function setTxnCrncy($TxnCrncy) {
		$this->TxnCrncy = $TxnCrncy;
	}
	public function getTxnCrncy() {
		return $this->TxnCrncy;
	}

	public function setTermLocInd($TermLocInd) {
		$this->TermLocInd = $TermLocInd;
	}
	public function getTermLocInd() {
		return $this->TermLocInd;
	}

	public function setCardCaptCap($CardCaptCap) {
		$this->CardCaptCap = $CardCaptCap;
	}
	public function getCardCaptCap() {
		return $this->CardCaptCap;
	}
	
	public function setGroupID($GroupID) {
		$this->GroupID = $GroupID;
	}
	public function getGroupID() {
		return $this->GroupID;
	}	
}

class TeleCheckECAGrp{
	private $chkType;
	private $chkClrkID;
	private $chkProdCd;
	private $dnlRecNum;
	private $chkNum;
	private $chkPhnNum;
	private $chkTrcID;
	private $chkBCN;
	private $chkExtMICR;

	public function setChkType($ChkType) {
		$this->ChkType = $ChkType;
	}
	public function getChkType() {
		return $this->ChkType;
	}

	public function setChkClrkID($ChkClrkID) {
		$this->ChkClrkID = $ChkClrkID;
	}
	public function getChkClrkID() {
		return $this->ChkClrkID;
	}

	public function setChkProdCd($ChkProdCd) {
		$this->ChkProdCd = $ChkProdCd;
	}
	public function getChkProdCd() {
		return $this->ChkProdCd;
	}

	public function setDnlRecNum($DnlRecNum) {
		$this->DnlRecNum = $DnlRecNum;
	}
	public function getDnlRecNum() {
		return $this->DnlRecNum;
	}

	public function setChkNum($ChkNum) {
		$this->ChkNum = $ChkNum;
	}
	public function getChkNum() {
		return $this->ChkNum;
	}

	public function setChkPhnNum($ChkPhnNum) {
		$this->ChkPhnNum = $ChkPhnNum;
	}
	public function getChkPhnNum() {
		return $this->ChkPhnNum;
	}

	public function setChkTrcID($ChkTrcID) {
		$this->ChkTrcID = $ChkTrcID;
	}
	public function getChkTrcID() {
		return $this->ChkTrcID;
	}

	public function setChkBCN($ChkBCN) {
		$this->ChkBCN = $ChkBCN;
	}
	public function getChkBCN() {
		return $this->ChkBCN;
	}

	public function setChkExtMICR($ChkExtMICR) {
		$this->ChkExtMICR = $ChkExtMICR;
	}
	public function getChkExtMICR() {
		return $this->ChkExtMICR;
	}
}

class CardGrp{
	private $acctNum;
	private $cardActivDate;
	private $cardExpiryDate;
	private $track1Data;
	private $track2Data;
	private $cardType;
	private $aVSResultCode;
	private $cCVInd;
	private $cCVData;
	private $cCVResultCode;

	public function setAcctNum($AcctNum) {
		$this->AcctNum = $AcctNum;
	}
	public function getAcctNum() {
		return $this->AcctNum;
	}

	public function setCardActivDate($CardActivDate) {
		$this->CardActivDate = $CardActivDate;
	}
	public function getCardActivDate() {
		return $this->CardActivDate;
	}

	public function setCardExpiryDate($CardExpiryDate) {
		$this->CardExpiryDate = $CardExpiryDate;
	}
	public function getCardExpiryDate() {
		return $this->CardExpiryDate;
	}

	public function setTrack1Data($Track1Data) {
		$this->Track1Data = $Track1Data;
	}
	public function getTrack1Data() {
		return $this->Track1Data;
	}

	public function setTrack2Data($Track2Data) {
		$this->Track2Data = $Track2Data;
	}
	public function getTrack2Data() {
		return $this->Track2Data;
	}

	public function setCardType($CardType) {
		$this->CardType = $CardType;
	}
	public function getCardType() {
		return $this->CardType;
	}

	public function setAVSResultCode($AVSResultCode) {
		$this->AVSResultCode = $AVSResultCode;
	}
	public function getAVSResultCode() {
		return $this->AVSResultCode;
	}

	public function setCCVInd($CCVInd) {
		$this->CCVInd = $CCVInd;
	}
	public function getCCVInd() {
		return $this->CCVInd;
	}

	public function setCCVData($CCVData) {
		$this->CCVData = $CCVData;
	}
	public function getCCVData() {
		return $this->CCVData;
	}

	public function setCCVResultCode($CCVResultCode) {
		$this->CCVResultCode = $CCVResultCode;
	}
	public function getCCVResultCode() {
		return $this->CCVResultCode;
	}
}

class VisaGrp{
	private $aCI;
	private $mrktSpecificDataInd;
	private $existingDebtInd;
	private $cardLevelResult;
	private $sourceReasonCode;
	private $transID;
	private $visaBID;
	private $visaAUAR;
	private $taxAmtCapablt;

	public function setACI($ACI) {
		$this->ACI = $ACI;
	}
	public function getACI() {
		return $this->ACI;
	}

	public function setMrktSpecificDataInd($MrktSpecificDataInd) {
		$this->MrktSpecificDataInd = $MrktSpecificDataInd;
	}
	public function getMrktSpecificDataInd() {
		return $this->MrktSpecificDataInd;
	}

	public function setExistingDebtInd($ExistingDebtInd) {
		$this->ExistingDebtInd = $ExistingDebtInd;
	}
	public function getExistingDebtInd() {
		return $this->ExistingDebtInd;
	}

	public function setCardLevelResult($CardLevelResult) {
		$this->CardLevelResult = $CardLevelResult;
	}
	public function getCardLevelResult() {
		return $this->CardLevelResult;
	}

	public function setSourceReasonCode($SourceReasonCode) {
		$this->SourceReasonCode = $SourceReasonCode;
	}
	public function getSourceReasonCode() {
		return $this->SourceReasonCode;
	}

	public function setTransID($TransID) {
		$this->TransID = $TransID;
	}
	public function getTransID() {
		return $this->TransID;
	}

	public function setVisaBID($VisaBID) {
		$this->VisaBID = $VisaBID;
	}
	public function getVisaBID() {
		return $this->VisaBID;
	}

	public function setVisaAUAR($VisaAUAR) {
		$this->VisaAUAR = $VisaAUAR;
	}
	public function getVisaAUAR() {
		return $this->VisaAUAR;
	}

	public function setTaxAmtCapablt($TaxAmtCapablt) {
		$this->TaxAmtCapablt = $TaxAmtCapablt;
	}
	public function getTaxAmtCapablt() {
		return $this->TaxAmtCapablt;
	}
}

class PINGrp{
	private $pINData;
	private $keySerialNumData;

	public function setPINData($PINData) {
		$this->PINData = $PINData;
	}
	public function getPINData() {
		return $this->PINData;
	}

	public function setKeySerialNumData($KeySerialNumData) {
		$this->KeySerialNumData = $KeySerialNumData;
	}
	public function getKeySerialNumData() {
		return $this->KeySerialNumData;
	}
}

class CheckRequestDetails{
	private $commonGrp;
	private $teleCheckECAGrp;
	private $addtlAmtGrp;
	private $altMerchNameAndAddrGrp;
	private $checkGrp;
	private $secrTxnGrp;

	public function setCommonGrp(CommonGrp $CommonGrp) {
		$this->CommonGrp = $CommonGrp;
	}
	public function getCommonGrp() {
		return $this->CommonGrp;
	}

	public function setTeleCheckECAGrp(TeleCheckECAGrp $TeleCheckECAGrp) {
		$this->TeleCheckECAGrp = $TeleCheckECAGrp;
	}
	public function getTeleCheckECAGrp() {
		return $this->TeleCheckECAGrp;
	}

	public function setAddtlAmtGrp(AddtlAmtGrp $AddtlAmtGrp) {
		$this->AddtlAmtGrp = $AddtlAmtGrp;
	}
	public function getAddtlAmtGrp() {
		return $this->AddtlAmtGrp;
	}

	public function setAltMerchNameAndAddrGrp(AltMerchNameAndAddrGrp $AltMerchNameAndAddrGrp) {
		$this->AltMerchNameAndAddrGrp = $AltMerchNameAndAddrGrp;
	}
	public function getAltMerchNameAndAddrGrp() {
		return $this->AltMerchNameAndAddrGrp;
	}

	public function setCheckGrp(CheckGrp $CheckGrp) {
		$this->CheckGrp = $CheckGrp;
	}
	public function getCheckGrp() {
		return $this->CheckGrp;
	}

	public function setSecrTxnGrp(SecrTxnGrp $SecrTxnGrp) {
		$this->SecrTxnGrp = $SecrTxnGrp;
	}
	public function getSecrTxnGrp() {
		return $this->SecrTxnGrp;
	}
}

class CreditRequestDetails{
	private $commonGrp;
	private $cardGrp;
	private $visaGrp;
	private $addtlAmtGrp;
	private $altMerchNameAndAddrGrp;
	private $secrTxnGrp;

	public function setCommonGrp(CommonGrp $CommonGrp) {
		$this->CommonGrp = $CommonGrp;
	}
	public function getCommonGrp() {
		return $this->CommonGrp;
	}

	public function setCardGrp(CardGrp $CardGrp) {
		$this->CardGrp = $CardGrp;
	}
	public function getCardGrp() {
		return $this->CardGrp;
	}

	public function setVisaGrp(VisaGrp $VisaGrp) {
		$this->VisaGrp = $VisaGrp;
	}
	public function getVisaGrp() {
		return $this->VisaGrp;
	}

	public function setAddtlAmtGrp(AddtlAmtGrp $AddtlAmtGrp) {
		$this->AddtlAmtGrp = $AddtlAmtGrp;
	}
	public function getAddtlAmtGrp() {
		return $this->AddtlAmtGrp;
	}
	
	public function setAltMerchNameAndAddrGrp(AltMerchNameAndAddrGrp $AltMerchNameAndAddrGrp) {
		$this->AltMerchNameAndAddrGrp = $AltMerchNameAndAddrGrp;
	}
	public function getAltMerchNameAndAddrGrp() {
		return $this->AltMerchNameAndAddrGrp;
	}

	public function setSecrTxnGrp(SecrTxnGrp $SecrTxnGrp) {
		$this->SecrTxnGrp = $SecrTxnGrp;
	}
	public function getSecrTxnGrp() {
		return $this->SecrTxnGrp;
	}
}

class DebitRequestDetails{
	private $commonGrp;
	private $cardGrp;
	private $pINGrp;
	private $altMerchNameAndAddrGrp;
	private $addtlAmtGrp;

	public function setCommonGrp(CommonGrp $CommonGrp) {
		$this->CommonGrp = $CommonGrp;
	}
	public function getCommonGrp() {
		return $this->CommonGrp;
	}

	public function setCardGrp(CardGrp $CardGrp) {
		$this->CardGrp = $CardGrp;
	}
	public function getCardGrp() {
		return $this->CardGrp;
	}

	public function setPINGrp(PINGrp $PINGrp) {
		$this->PINGrp = $PINGrp;
	}
	public function getPINGrp() {
		return $this->PINGrp;
	}

	public function setAltMerchNameAndAddrGrp(AltMerchNameAndAddrGrp $AltMerchNameAndAddrGrp) {
		$this->AltMerchNameAndAddrGrp = $AltMerchNameAndAddrGrp;
	}
	public function getAltMerchNameAndAddrGrp() {
		return $this->AltMerchNameAndAddrGrp;
	}

	public function setAddtlAmtGrp(AddtlAmtGrp $AddtlAmtGrp) {
		$this->AddtlAmtGrp = $AddtlAmtGrp;
	}
	public function getAddtlAmtGrp() {
		return $this->AddtlAmtGrp;
	}
}

class GMFMessageVariants{
	private $checkRequest;
	private $creditRequest;
	private $debitRequest;
	private $repeat;

	public function setCheckRequest($CheckRequest) {
		$this->CheckRequest = $CheckRequest;
	}
	public function getCheckRequest() {
		return $this->CheckRequest;
	}

	public function setCreditRequest($CreditRequest) {
		$this->CreditRequest = $CreditRequest;
	}
	public function getCreditRequest() {
		return $this->CreditRequest;
	}

	public function setDebitRequest($DebitRequest) {
		$this->DebitRequest = $DebitRequest;
	}
	public function getDebitRequest() {
		return $this->DebitRequest;
	}

	public function setRepeat($Repeat) {
		$this->Repeat = $Repeat;
	}
	public function getRepeat() {
		return $this->Repeat;
	}
}
