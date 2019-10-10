package com.example.visualize;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import java.nio.ByteBuffer;


public class QRDetector {
    private BarcodeDetector qrCodeDetector;
    private boolean qrDetected;
    private Barcode barcodeValue;

  QRDetector(Context context){
      setUpBarcodeDetector(context);
  }

  private void setUpBarcodeDetector(Context context){
      qrCodeDetector = new BarcodeDetector.Builder(context)
              .setBarcodeFormats(Barcode.QR_CODE)
              .build();
      if(!qrCodeDetector.isOperational()){
          Toast.makeText(context, "Could not set up the detector!", Toast.LENGTH_SHORT).show();
          return;
      }
  }


  public void detectQR(ByteBuffer byteBuffer, int height, int width, int format){
      qrDetected = false;
      Frame frame = new Frame.Builder().setImageData(byteBuffer, width, height, format).build();
      SparseArray<Barcode> barcodes = qrCodeDetector.detect(frame);
      if(barcodes.size() > 0) {
          barcodeValue = barcodes.valueAt(0);
          if(barcodeValue.valueFormat == Barcode.URL){
              String value = barcodeValue.displayValue;
              if(value.contains(".")){
                  String extension = value.substring(value.lastIndexOf(".") + 1);
                  if(extension.equals("gltf")){
                      qrDetected = true;
                  }
              }
          }
      }
  }

  public Barcode getQrValue(){
      return barcodeValue;
  }

  public boolean isQRDetected(){
      return qrDetected;
  }
}
