package dev.phyce.naturalspeech.texttospeech.engine.macos.natives.coreaudiotypes;

import com.sun.jna.Structure;

/*
struct AudioBufferList
{
    UInt32 mNumberBuffers;
    AudioBuffer mBuffers[1]; // this is a variable length array of mNumberBuffers elements

#if defined(__cplusplus) && defined(CA_STRICT) && CA_STRICT
public:
    AudioBufferList() {}
private:
    //  Copying and assigning a variable length struct is problematic; generate a compile error.
    AudioBufferList(const AudioBufferList&);
    AudioBufferList&    operator=(const AudioBufferList&);
#endif

};
 */
public class AudioBufferList extends Structure {
}
