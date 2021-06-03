package app.actionmobile.sobtex


import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import app.actionmobile.sobtex.databinding.FragmentFirstBinding
import java.io.*
import java.util.*


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    var myLabel: TextView? = null
    var myTextbox: EditText? = null
    lateinit var fileNameEdit : EditText
    var mBluetoothAdapter: BluetoothAdapter? = null
    var mmSocket: BluetoothSocket? = null
    var mmDevice: BluetoothDevice? = null
    var mmOutputStream: OutputStream? = null
    var mmInputStream: InputStream? = null
    var workerThread: Thread? = null
    lateinit var readBuffer: ByteArray
    var readBufferPosition = 0
    var counter = 0

    private var btDeviceSpinner: Spinner? = null
    private var adapter: ArrayAdapter<String>? = null
    var listViewItems = ArrayList<String>()
    lateinit var btCurrentDeviceName : String
    lateinit var pairedDevices: Set<BluetoothDevice>
    lateinit var btAdapter: BluetoothAdapter
    var isRetrievingFile : Boolean = false;
    lateinit var outputFileName : String

    @Volatile
    var stopWorker = false


    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val openButton: Button = view.findViewById(R.id.open) as Button
        val sendButton: Button = view.findViewById(R.id.send) as Button
        val closeButton: Button = view.findViewById(R.id.close) as Button
        val getFileButton: Button = view.findViewById(R.id.getFile) as Button

        myLabel = view.findViewById(R.id.label) as TextView
        myTextbox = view.findViewById(R.id.entry) as EditText
        fileNameEdit = view.findViewById(R.id.fileName) as EditText


        adapter =
            activity?.let { ArrayAdapter<String>(it, R.layout.support_simple_spinner_dropdown_item, listViewItems) }
        btDeviceSpinner = view.findViewById(R.id.btDeviceSpinner) as Spinner
        btDeviceSpinner?.adapter = adapter

        btAdapter = BluetoothAdapter.getDefaultAdapter()
        pairedDevices = GetPairedDevices(btAdapter)
        if (pairedDevices.count() <= 0){
            // just in case user is running in emulator, they will see two items in list
            adapter?.add("first");
            adapter?.add("second");
            adapter?.notifyDataSetChanged()
        }

        btDeviceSpinner!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {

            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                btCurrentDeviceName = btDeviceSpinner?.getSelectedItem().toString();
                //saveDeviceNamePref();
                Log.d("FirstFrag", "DeviceInfo : " + btCurrentDeviceName);
                //logViewAdapter.add("DeviceInfo : " + btCurrentDeviceName);
                //logViewAdapter.notifyDataSetChanged();

            }
        }

        getFileButton.setOnClickListener(View.OnClickListener {
            try {
                if (fileNameEdit.text.toString() != ""){
                    outputFileName = fileNameEdit.text.toString();
                    isRetrievingFile = true;
                    sendData()
                }
                else{
                    var errMsg : String = "Please provide a file name to save your data to.\n"
                    errMsg += "Examples => temperature.txt, sampledata.txt, sample.log"
                    Log.i("FirstFrag",errMsg)
                    myLabel?.text = errMsg
                }

            } catch (ex: IOException) {
            }
        })
        //Open Button
        openButton.setOnClickListener(View.OnClickListener {
            try {
                findBT()
                openBT()
            } catch (ex: IOException) {
            }
        })

        //Send Button

        //Send Button
        sendButton.setOnClickListener(View.OnClickListener {
            try {
                sendData()
            } catch (ex: IOException) {
            }
        })

        //Close button

        //Close button
        closeButton.setOnClickListener(View.OnClickListener {
            try {
                closeBT()
            } catch (ex: IOException) {
            }
        })
    }

    fun findBT() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter() as BluetoothAdapter
        if (mBluetoothAdapter == null) {
            myLabel!!.text = "No bluetooth adapter available"
        }
        if (mBluetoothAdapter?.isEnabled() != true) {
            val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetooth, 0)
        }
        val pairedDevices = mBluetoothAdapter?.getBondedDevices()
        if (pairedDevices?.size?.compareTo(0)  != 0) {
            for (device in pairedDevices!!) {
                if (device.name == btCurrentDeviceName) {
                    mmDevice = device
                    break
                }
            }
        }
        myLabel!!.text = "Bluetooth Device Found"
    }

    @Throws(IOException::class)
    fun openBT() {
        val uuid: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") //Standard SerialPortService ID
        mmSocket = mmDevice!!.createRfcommSocketToServiceRecord(uuid)
        mmSocket?.connect()
        mmOutputStream = mmSocket?.getOutputStream()
        mmInputStream = mmSocket?.getInputStream()
        beginListenForData()
        myLabel!!.text = "Bluetooth Opened"
        // UUID strings can be upper or lowercase (they are equivalent)
        // 00001101-0000-1000-8000-00805f9b34fb
        // 00001101-0000-1000-8000-00805F9B34FB
    }

    fun beginListenForData() {
        //val handler : Handler//= Handler()
        // Changed following line from 10 (/n newline) to CR (13) to
        val delimiter: Byte = 13 //This is the ASCII code for a newline character
        stopWorker = false
        readBufferPosition = 0
        readBuffer = ByteArray(1024)
        workerThread = Thread {
            while (!Thread.currentThread().isInterrupted && !stopWorker) {
                try {
                    val bytesAvailable: Int? = mmInputStream?.available()
                    if (bytesAvailable!! > 0) {
                        Log.i("FirstFrag", "bytesAvail : " + bytesAvailable.toString())
                        val packetBytes = ByteArray(bytesAvailable)
                        mmInputStream?.read(packetBytes)
                        Log.i("FirstFrag", packetBytes.toString())
                        for (i in 0 until bytesAvailable) {
                            val b = packetBytes[i]
                            Log.i("FirstFrag", "b :" + b.toString())
                            if (b == delimiter) {
                                val encodedBytes = ByteArray(readBufferPosition)
                                System.arraycopy(
                                    readBuffer,
                                    0,
                                    encodedBytes,
                                    0,
                                    encodedBytes.size
                                )
                                val data = encodedBytes.toString(Charsets.US_ASCII)//  String(encodedBytes, "US-ASCII")
                                readBufferPosition = 0
                                if (isRetrievingFile) {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        var msg = writeFileExternalStorage(encodedBytes)
                                        myLabel?.text = msg
                                    }, 10)
                                }
                                else{
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        myLabel!!.text = data
                                    }, 10)
                                }
                                //handler.post( {  })
                                Log.i("FirstFrag", data)
                            } else {
                                readBuffer[readBufferPosition++] = b
                            }
                        }
                    }
                } catch (ex: IOException) {
                    stopWorker = true
                }
            }
        }
        workerThread!!.start()
    }

    fun writeFileExternalStorage(encodedBytes: ByteArray) :String {

        //Text of the Document

        Log.i("FirstFrag", "in writeFileExternalStorage")
        //Checking the availability state of the External Storage.
        val state = Environment.getExternalStorageState()
        if (Environment.MEDIA_MOUNTED != state) {

            Log.i("FirstFrag", "Couldn't get file system.")
            return "Couldn't write file."
        }

        //Create a new file that points to the root directory, with the given name:
        //var allDirs  = getExternalFilesDirs()

        var file =File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), outputFileName)

        Log.i("FirstFrag", file.absolutePath)
        //This point and below is responsible for the write operation
        var outputStream: FileOutputStream? = null
        try {
            Log.i("FirstFrag", "creating new file")
            file.createNewFile()
            Log.i("FirstFrag", "file created")
            //second argument of FileOutputStream constructor indicates whether
            //to append or create new file if one exists
            outputStream = FileOutputStream(file, true)
            outputStream?.write(encodedBytes)
            outputStream?.flush()
            outputStream?.close()
            Log.i("FirstFrag", "wrote file!")
            return file.absolutePath
        } catch (e: Exception) {
            Log.i("FirstFrag",e.message.toString())
            return e.message.toString() + "\nPlease check that you have a valid filename."
        }
        finally {
            isRetrievingFile = false;
        }
        //return file.absolutePath
    }

    @Throws(IOException::class)
    fun sendData() {
        var msg = myTextbox!!.text.toString()
        // msg += "\n"
        mmOutputStream?.write(msg.toByteArray())
        myLabel!!.text = "Data Sent"
    }

    @Throws(IOException::class)
    fun closeBT() {
        stopWorker = true
        mmOutputStream?.close()
        mmInputStream?.close()
        mmSocket!!.close()
        myLabel!!.text = "Bluetooth Closed"
    }

    private fun GetPairedDevices(btAdapter: BluetoothAdapter): MutableSet<BluetoothDevice> {
        val pairedDevices = btAdapter?.bondedDevices
        // If there are paired devices
        Log.i("FirstFrag", "checking paired devices")
        if (pairedDevices != null) {
            Log.i("FirstFrag", "pairedDevices NOT null")
            if (pairedDevices.size > 0) {
                Log.i("FirstFrag", pairedDevices.size.toString())
                for (device in pairedDevices) {
                    // Add the name and address to an array adapter to show in a ListView
                    adapter!!.add(device.name) // + "\n" + device.getAddress());
                }
                adapter!!.notifyDataSetChanged()
            }
        }
        return pairedDevices
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

