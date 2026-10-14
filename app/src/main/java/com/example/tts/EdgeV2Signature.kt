package com.example.tts

import java.nio.charset.StandardCharsets

/**
 * Edge Read Aloud v2 connection signature, ported from rany2/edge-tts
 * src/edge_tts/drm.py (LGPL-3.0-or-later). Retain this attribution.
 *
 * The undocumented Microsoft endpoint can still reject clients with HTTP 403.
 */
internal object EdgeV2Signature {
  private val initial = intArrayOf(0x6A09E667.toInt(),0xBB67AE85.toInt(),0x3C6EF372.toInt(),0xA54FF53A.toInt(),0x510E527F.toInt(),0x9B05688C.toInt(),0x1F83D9AB.toInt(),0x5BE0CD19.toInt())
  private val seed = intArrayOf(0xFD9FBEA2.toInt(),0x91243CE7.toInt(),0xBE499094.toInt(),0xCD2E1E0A.toInt(),0x6C006F49.toInt(),0x04BD35AB.toInt(),0xFEB0A7E4.toInt(),0x03EFFBD2.toInt())
  private val schedule = intArrayOf(0xEB24BF9E.toInt(),0x1558691F.toInt(),0xA0C09EE2.toInt(),0x0CFA32BA.toInt(),0xA622EE8C.toInt(),0xD1DD9491.toInt(),0xC0DD25A0.toInt(),0xF9E70AB6.toInt(),0xF0C31573.toInt(),0x7058CE4E.toInt(),0x7188E606.toInt(),0x00E5AB33.toInt(),0xD258B060.toInt(),0x72766564.toInt())
  private val k = intArrayOf(0x428A2F98.toInt(),0x71374491.toInt(),0xB5C0FBCF.toInt(),0xE9B5DBA5.toInt(),0x3956C25B.toInt(),0x59F111F1.toInt(),0x923F82A4.toInt(),0xAB1C5ED5.toInt(),0xD807AA98.toInt(),0x12835B01.toInt(),0x243185BE.toInt(),0x550C7DC3.toInt(),0x72BE5D74.toInt(),0x80DEB1FE.toInt(),0x9BDC06A7.toInt(),0xC19BF174.toInt(),0xE49B69C1.toInt(),0xEFBE4786.toInt(),0x0FC19DC6.toInt(),0x240CA1CC.toInt(),0x2DE92C6F.toInt(),0x4A7484AA.toInt(),0x5CB0A9DC.toInt(),0x76F988DA.toInt(),0x983E5152.toInt(),0xA831C66D.toInt(),0xB00327C8.toInt(),0xBF597FC7.toInt(),0xC6E00BF3.toInt(),0xD5A79147.toInt(),0x06CA6351.toInt(),0x14292967.toInt(),0x27B70A85.toInt(),0x2E1B2138.toInt(),0x4D2C6DFC.toInt(),0x53380D13.toInt(),0x650A7354.toInt(),0x766A0ABB.toInt(),0x81C2C92E.toInt(),0x92722C85.toInt(),0xA2BFE8A1.toInt(),0xA81A664B.toInt(),0xC24B8B70.toInt(),0xC76C51A3.toInt(),0xD192E819.toInt(),0xD6990624.toInt(),0xF40E3585.toInt(),0x106AA070.toInt(),0x19A4C116.toInt(),0x1E376C08.toInt(),0x2748774C.toInt(),0x34B0BCB5.toInt(),0x391C0CB3.toInt(),0x4ED8AA4A.toInt(),0x5B9CCA4F.toInt(),0x682E6FF3.toInt(),0x748F82EE.toInt(),0x78A5636F.toInt(),0x84C87814.toInt(),0x8CC70208.toInt(),0x90BEFFFA.toInt(),0xA4506CEB.toInt(),0xBEF9A3F7.toInt(),0xC67178F2.toInt())
  private val coeffs = intArrayOf(0xE82E91B1.toInt(),0x44DE24C6.toInt(),0x3BEB65BC.toInt(),0xC89C5933.toInt(),0x8D1EC211.toInt(),0x314EF5D5.toInt(),0xE92491B1.toInt(),0xF0A5B9D5.toInt(),0x06064CF3.toInt(),0xDA79FA60.toInt(),0xBED20560.toInt(),0x89F9C9F8.toInt(),0x35FA38C6.toInt(),0xCB49E3C6.toInt())
  private val offsets = intArrayOf(0xB32700D0.toInt(),0x88DFF51D.toInt(),0x29514038.toInt(),0xB328CBDF.toInt(),0xD405B4D5.toInt(),0x26E55F3C.toInt(),0x607801C6.toInt(),0x82B2C1FB.toInt(),0xFA51A3DA.toInt(),0x207CC464.toInt(),0x3C3C74F3.toInt(),0xD2D14B61.toInt(),0xBB04A2C1.toInt(),0xC5626167.toInt())

  private fun rol16(x: Int): Int = (x shl 16) or (x ushr 16)
  private fun ror(x: Int, n: Int): Int = (x ushr n) or (x shl (32-n))
  private fun s0(x: Int): Int = ror(x,7) xor ror(x,18) xor (x ushr 3)
  private fun s1(x: Int): Int = ror(x,17) xor ror(x,19) xor (x ushr 10)
  private fun b0(x: Int): Int = ror(x,2) xor ror(x,13) xor ror(x,22)
  private fun b1(x: Int): Int = ror(x,6) xor ror(x,11) xor ror(x,25)

  private fun expand(w: MutableList<Int>, from: Int = 16) {
    for (i in from until 64) w.add(s1(w[i-2])+w[i-7]+s0(w[i-15])+w[i-16])
  }
  private fun applyRounds(state: IntArray, w: List<Int>, start: Int = 0,
                          special: Boolean = false): IntArray {
    var a=state[0];var b=state[1];var c=state[2];var d=state[3]
    var e=state[4];var f=state[5];var g=state[6];var h=state[7]
    for (i in start until 64) {
      val ki = if(special && i==14) 0xC9DC06A7.toInt() else k[i]
      val t1=h+b1(e)+(g xor (e and (f xor g)))+ki+w[i]
      val t2=b0(a)+((a and b) or (c and (a or b)))
      h=g;g=f;f=e;e=d+t1;d=c;c=b;b=a;a=t1+t2
    }
    return intArrayOf(a,b,c,d,e,f,g,h)
  }
  private fun intBE(bytes: ByteArray, pos: Int): Int =
    ((bytes[pos].toInt() and 255) shl 24) or
    ((bytes[pos+1].toInt() and 255) shl 16) or
    ((bytes[pos+2].toInt() and 255) shl 8) or (bytes[pos+3].toInt() and 255)
  private fun intLE(bytes: ByteArray, pos: Int): Int =
    (bytes[pos].toInt() and 255) or
    ((bytes[pos+1].toInt() and 255) shl 8) or
    ((bytes[pos+2].toInt() and 255) shl 16) or
    ((bytes[pos+3].toInt() and 255) shl 24)
  private fun digest(data: ByteArray): ByteArray {
    require(data.size >= 7)
    val words = MutableList(14){0}
    words.add(((data[0].toInt() and 255) shl 16) or
      ((data[1].toInt() and 255) shl 8) or (data[2].toInt() and 255))
    words.add(intBE(data,3))
    for(i in 16 until 30) {
      var v=schedule[i-16]
      if(i-2>=14) {val x=s1(words[i-2]);v+=if(i==16) x xor 0x000B92C0 else x}
      if(i-7>=14) v+=words[i-7]
      if(i-15>=14) {val x=s0(words[i-15]);v+=if(i==29) x xor 0x059C0B80 else x}
      words.add(v)
    }
    words.add(s1(words[28])+words[23]+s0(words[15])+words[14]+
      0xC9DC06A7.toInt()-k[14])
    expand(words,31)
    val state=applyRounds(seed,words,14,true)
      .mapIndexed { i,v -> v+initial[i] }.toIntArray()
    val tail=data.copyOfRange(7,data.size).toMutableList()
    tail.add(0x80.toByte())
    val size=57+data.size
    repeat((55-size).mod(64)){tail.add(0)}
    val bitSize=size.toLong()*8L
    for(shift in 56 downTo 0 step 8)tail.add((bitSize ushr shift).toByte())
    check(tail.size%64==0)
    for(start in tail.indices step 64) {
      val block=ByteArray(64){tail[start+it]}
      val w=MutableList(16){intBE(block,it*4)}
      expand(w)
      val next=applyRounds(state,w)
      for(i in 0 until 8)state[i]+=next[i]
    }
    val out=ByteArray(32)
    for(i in state.indices) for(j in 0..3)
      out[i*4+j]=(state[i] ushr (24-8*j)).toByte()
    return out
  }
  private fun cs64(data: ByteArray, sha: ByteArray): Pair<Int,Int> {
    val keyA=intLE(sha,0) or 1
    val keyB=intLE(sha,4) or 1
    val factors=intArrayOf(keyA*0x82893933.toInt(),keyB*0x82893933.toInt())
    val terms=mutableListOf<Int>();val shifts=mutableListOf<Int>()
    for(i in coeffs.indices) {terms.add(coeffs[i]*factors[i%2]);shifts.add(offsets[i])}
    val raw=byteArrayOf(0)+data
    val padded=raw.copyOf((raw.size+3)/4*4)
    val count=if((padded.size/4)%2==0)padded.size/4 else padded.size/4+1
    for(i in 0 until count) {
      val x=if(i*4<padded.size)intLE(padded,i*4) else 0
      var t=x*(if(i%2==0)keyA else keyB)
      var off=rol16(x)*(if(i%2==0)0xEF0569FB.toInt() else 0xC31713DB.toInt())
      if(i==0) {t+=0xF2F90B1A.toInt()*factors[0];off+=0x0B1A0000}
      terms.add(t);shifts.add(off)
    }
    var swapAcc=0;var swapLast=0;var state=0;var acc=0
    for(i in terms.indices step 2) {
      val x=terms[i];val y=terms[i+1]
      var t=rol16(shifts[i]+x)
      var q=t*0x76F50F34
      swapAcc+=rol16(q)*0xC3EFEA97.toInt()+q
      t=rol16(shifts[i+1]+y)
      q=t*0x1881CE3C
      swapLast=rol16(q)*0x35BD1EC9+q
      swapAcc+=swapLast
      state=state*keyA+x
      state=rol16(state)*0xCF98B111.toInt()
      state=rol16(state)*0x87085B9F.toInt()
      state=rol16(state)*0x12CEB96D
      state=rol16(state)*0x257E1D83
      acc+=state
      t=state*keyB+y
      t=rol16(t)*0xA27416F5.toInt()
      t=rol16(t)*0xD38396FF.toInt()
      state=rol16(rol16(t)*0x7C932B89)*0xBFA49F69.toInt()
      acc+=state
    }
    return Pair(swapLast xor state,swapAcc xor acc)
  }
  private fun hex(bytes: ByteArray): String =
    bytes.joinToString(""){"%02X".format(it.toInt() and 255)}
  private fun hexLE(value: Int): String =
    hex(ByteArray(4){(value ushr (8*it)).toByte()})
  fun generate(unixSeconds: Long, token: String): String {
    val tick=unixSeconds+11644473600L
    val rounded=tick-tick%300L
    val input=((rounded*10000000L).toString()+token).toByteArray(StandardCharsets.US_ASCII)
    val sha=digest(input)
    val (a,b)=cs64(input,sha)
    return hexLE(a)+hexLE(b)+"-"+hex(sha)
  }
}
