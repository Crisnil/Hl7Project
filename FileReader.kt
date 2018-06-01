package com.acemc.hisd3.service.HL7

import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.HL7Exception
import ca.uhn.hl7v2.app.Connection
import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.parser.CanonicalModelClassFactory
import org.apache.poi.hssf.usermodel.HeaderFooter.file
import ca.uhn.hl7v2.util.Hl7InputStreamMessageIterator
import com.acemc.hisd3.repository.revenuecenter.LabResultItemRepository
import com.acemc.hisd3.repository.revenuecenter.OrderSlipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.*
import java.lang.System.out
import javax.inject.Inject


@Transactional
@Service
class Hl7FileReaderService {

    @Inject
    internal var hl7MsgProcessor: Hl7MsgProcessor?=null

    @Inject
    internal var orderSlipRepository: OrderSlipRepository?=null

    @Inject
    internal var labResultItemRepository: LabResultItemRepository?=null

    fun readMessage(theFile: File,theMetadata : Map<String, Any>?) {

        var context = DefaultHapiContext()
        var mcf = CanonicalModelClassFactory("2.5")
        context.setModelClassFactory(mcf)
        var parser = context.getPipeParser()

        try {
            val inStream = FileInputStream(theFile)
            val buffInStream = BufferedInputStream(inStream)
            val iter = Hl7InputStreamMessageIterator(buffInStream)

            var conn: Connection? = null

            while (iter.hasNext()) {

                var next = iter.next()
               // hl7MsgProcessor?.processORU(next, theMetadata)
                if (conn == null) {
                    val useTls = false // Should we use TLS/SSL?
                    conn = context.newClient("127.0.0.1", 22222, useTls)

                }
                try {
                    var initiator = conn?.initiator
                    var response = initiator?.sendAndReceive(next)
                    val responseString = parser.encode(response)
                    System.out.println("Printing Encoded Message: " + responseString)

                } catch (e: IOException) {
                   /// throw IllegalArgumentException(e.message)
                   // throw HL7Exception(e)
                    System.out.println("Didn't send out this message!")
                    conn?.close()
                    conn = null
                }
            }
            inStream.close()
        } catch (e: IOException) {
            System.err.println("Missing file ")
        }
    }
}
