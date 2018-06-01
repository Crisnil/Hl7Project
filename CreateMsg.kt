package com.acemc.hisd3.service.HL7

import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.HL7Exception
import ca.uhn.hl7v2.HapiContext
import ca.uhn.hl7v2.model.v25.message.ADT_A01
import ca.uhn.hl7v2.model.v25.message.ORM_O01
import ca.uhn.hl7v2.model.v25.segment.MSA
import ca.uhn.hl7v2.model.v25.segment.MSH
import ca.uhn.hl7v2.model.v25.segment.PID
import ca.uhn.hl7v2.parser.CanonicalModelClassFactory
import ca.uhn.hl7v2.parser.Parser
import ca.uhn.hl7v2.util.Hl7InputStreamMessageIterator
import ca.uhn.hl7v2.util.idgenerator.InMemoryIDGenerator
import com.acemc.hisd3.domain.Employee
import com.acemc.hisd3.domain.revenuecenter.Hl7Config
import com.acemc.hisd3.domain.revenuecenter.OrderSlip
import com.acemc.hisd3.domain.revenuecenter.OrderSlipLog
import com.acemc.hisd3.repository.EmployeeRepository
import com.acemc.hisd3.repository.HospitalInfoRepository
import com.acemc.hisd3.repository.PatientRepository
import com.acemc.hisd3.repository.revenuecenter.Hl7ConfigRepository
import com.acemc.hisd3.repository.revenuecenter.OrderSlipLogsRepository
import com.acemc.hisd3.repository.revenuecenter.OrderSlipRepository
import com.acemc.hisd3.repository.revenuecenter.RevenueCenterRepository
import jcifs.smb.NtlmPasswordAuthentication
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileOutputStream
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import javax.inject.Inject
import org.joda.time.DateTimeZone
import org.springframework.boot.autoconfigure.jms.activemq.ActiveMQProperties
import org.springframework.transaction.annotation.Transactional
import java.io.*
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


@RestController
@RequestMapping("/restapi/createmsg")
@Transactional
  class CreateHl7Message {
     /**
       * @param args
       * @throws HL7Exception
      */
     @Inject
     internal var orderSlipRepository: OrderSlipRepository?=null

     @Inject
     internal var revenueCenterRespository:RevenueCenterRepository?=null

      @Inject
     internal var hospitalInfoRepository:HospitalInfoRepository?=null

      @Inject
     internal var patientsRepository:PatientRepository?=null

    @Inject
    internal var employeeRepository:EmployeeRepository?=null

    @Inject
    internal var hl7Repository:Hl7ConfigRepository?=null

    @Inject
    lateinit var orderSlipLogsRepository: OrderSlipLogsRepository

    @RequestMapping(value="/msgorm",method = arrayOf(RequestMethod.POST))
      fun msgorm(@RequestParam  id: UUID,
                 @RequestParam(required = false) empId:UUID?
    ) {
          var docId : UUID? = null
          var activePdscId : UUID? =null
          var requstingdoctor:Employee? = null
          var hl7client:Hl7Config?= null
          var orderspecific = orderSlipRepository?.findOrderSlipById(id)
                if(orderspecific != null){
                    docId = orderspecific?.docorder?.doctor?.id
                    activePdscId = orderspecific?.docorder?.erPmr?.pdsc?.id
                    hl7client = hl7Repository?.findActive(orderspecific?.revenuecenter_id?.id!!)?.firstOrNull()
                }
          var hospital = hospitalInfoRepository?.findAll()?.firstOrNull()
          var sendingFacility = revenueCenterRespository?.findOneByRevenuecenterId(orderspecific?.revenuecenter_id?.id)
          var patient = patientsRepository?.findOne(orderspecific?.patientID)


         if(docId != null) {
             requstingdoctor = employeeRepository?.findAllByEmpId(docId!!)?.firstOrNull()
         }

     //   var hl7Client = hl7Repository.findByRevenueId(orderspecific?.revenuecenter_id?.id)
         //initialize the Hl7 encoder

          var context = DefaultHapiContext()
          var mcf = CanonicalModelClassFactory("2.5")
          context.setModelClassFactory(mcf)

          var orm = ORM_O01()
          orm.initQuickstart("ORM", "O01", "D")
          var parser = context.getPipeParser()
          parser.getParserConfiguration().setIdGenerator(InMemoryIDGenerator())



          // Populate the MSH Segment
          var msh = orm.getMSH()
          msh.getSendingApplication().getNamespaceID().setValue(hospital?.hospital_name)
          msh.getSendingFacility().getNamespaceID().setValue(sendingFacility?.name)
          msh.dateTimeOfMessage.time

          // Populate the PID Segment
          var pid = orm.getPATIENT().getPID()
          pid.getPatientName(0).getFamilyName().getSurname().setValue(patient?.lastname)
          pid.getPatientName(0).getGivenName().setValue(patient?.firstname)
          pid.getPatientName(0).getSuffixEgJRorIII().setValue(patient?.extensionname?:"")
          pid.getDateTimeOfBirth().time.value= patient?.dob?.toString("yyyyMMddHHmm")
          pid.getPatientAddress(0).getCity().setValue(patient?.current_city)
          pid.getPatientAddress(0).getCountry().setValue(patient?.current_country)
        pid.getPatientAddress(0).streetAddress.streetName.value=patient?.current_address?:""
        pid.getPatientAddress(0).stateOrProvince.value=patient?.current_province
        pid.getPatientAddress(0).zipOrPostalCode.value=patient?.current_zip
          pid.patientID.idNumber.value=patient?.patientNo
          pid.getPatientIdentifierList(0).idNumber.value=patient?.patientNo
          pid.administrativeSex.value=patient?.gender
        pid.getCitizenship(0).identifier.value=patient?.citizenship

          var pv1 = orm.getPATIENT().getPATIENT_VISIT().getPV1()
          pv1.getVisitNumber().getIDNumber().setValue(orderspecific?.orderslipno?:"")
          pv1.visitNumber.idNumber.value=patient?.patientNo
        pv1.getAttendingDoctor(0).givenName.value=requstingdoctor?.firstname?:""
        pv1.getAttendingDoctor(0).familyName.surname.value=requstingdoctor?.lastname?:""
        pv1.getAttendingDoctor(0).idNumber.value=requstingdoctor?.employeeid?:""
        pv1.getAdmittingDoctor(0).givenName

        var orc = orm.getORDER(0).getORC()
        orc.orc1_OrderControl.value="NW"
        orc.getPlacerOrderNumber().universalID.value=orderspecific?.orderslipno

        var obr = orm.getORDER(0).getORDER_DETAIL().getOBR()
        obr.setIDOBR.value=orderspecific?.orderslipno
        obr.placerOrderNumber.universalID.value=orderspecific?.orderslipno
        obr.obr4_UniversalServiceIdentifier.ce1_Identifier.value=orderspecific?.serviceFee?.serviceCode
        obr.obr4_UniversalServiceIdentifier.ce2_Text.value=orderspecific?.servicefeedesc
        obr.obr6_RequestedDateTime.time.value=orderspecific?.timeStarted?.toString("yyyyMMddHHmm")
        obr.obr7_ObservationDateTime.time.value=orderspecific?.startUtilization?.toString("yyyyMMddHHmm")

          /*
           * In other situation, more segments and fields would be populated
           */
          // Now, let's encode the message and look at the output

        var  encodedMessage = parser.encode(orm)

         val useTls = false // Should we use TLS/SSL?

        if(hl7client != null) {
            if (hl7client.useTcp == true) {

                try {
                    var connection = context.newClient(hl7client.ipAddress, hl7client.port!!, useTls)
                    var initiator = connection.initiator

                    var response = initiator.sendAndReceive(orm)
                    val responseString = parser.encode(response)

                    System.out.println("Printing ER7 Encoded Message: " + response)
//
//            var msa = parser.parse(responseString) as ca.uhn.hl7v2.model.v25.segment.MSA
//            var msaStatus = msa.msa1_AcknowledgmentCode.name

                } catch (e: IOException) {
                    throw IllegalArgumentException(e.message)
                    throw HL7Exception(e)
                }
            }
            else{

               try {
                   /** writting files to shared folder in a network wiht credentials**/
                   val ntlmPasswordAuthentication = NtlmPasswordAuthentication(hl7client.ipAddress, hl7client.userName, hl7client.password)
                   val user = hl7client.userName +":"+ hl7client.password
                   val auth = NtlmPasswordAuthentication(user)

                   val smbUrl = "smb://"+hl7client.ipAddress+"/"+hl7client.directory+"/New"
                   val directory = SmbFile(smbUrl,ntlmPasswordAuthentication)

                   try{
                       if (! directory.exists()) {
                           directory.mkdir()
                       }
                   }catch(e: IOException) {
                       throw IllegalArgumentException(e.message)
                       e.printStackTrace()
                   }

                   val path = "smb://"+hl7client.ipAddress+"/"+hl7client.directory+"/New/"+orderspecific?.orderslipno+".hl7"
                   val sFile = SmbFile(path, ntlmPasswordAuthentication)
                   var sfos =  SmbFileOutputStream(sFile)
                   sfos.write(encodedMessage.toByteArray())
                   sfos.close()

                    /*** writting files in local shared folder***/
                    var file = Paths.get("//localhost/Shared/Outbox/"+orderspecific?.orderslipno+".hl7")
                    Files.write(file,encodedMessage.toByteArray())


                }catch(e: IOException) {
                   throw IllegalArgumentException(e.message)
                   e.printStackTrace()
               }
            }
        }

          /*
           * Prints:
           *
           * MSH|^~\&|TestSendingSystem||||200701011539||ADT^A01^ADT A01||||123
           * PID|||123456||Doe^John
           */

         // Next, let's use the XML parser to encode as XML
//         var parser2 = context.getXMLParser();
//          encodedMessage = parser2.encode(adt);
//          System.out.println("Printing XML Encoded Message:");
//          System.out.println(encodedMessage);

        if(orderspecific != null){
            orderspecific.submittedTo3rdparty=true
            orderSlipRepository?.save(orderspecific)
            var attendingPersonelEmp:Employee? = employeeRepository?.findOne(empId)
            val newLog = OrderSlipLog()
            var Logs= """Order Status:${orderspecific.status}
Queue Started: ${org.joda.time.LocalDateTime()}
Schedule:${org.joda.time.LocalDateTime(orderspecific.timeStarted)}
Notes : Pushed HL7 MSG to RIS/LIS"""
            newLog.description = Logs
            newLog.orderslip = orderspecific
            newLog.attending_personel = attendingPersonelEmp
            orderSlipLogsRepository.save(newLog)
        }
        return
     }

 }
