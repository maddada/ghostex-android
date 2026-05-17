     .global blob
     .global blob_size
     .section .rodata
 blob:
 #if defined __i686__
     .incbin GHOSTEX_BOOTSTRAP_I686
 #elif defined __x86_64__
     .incbin GHOSTEX_BOOTSTRAP_X86_64
 #elif defined __aarch64__
     .incbin GHOSTEX_BOOTSTRAP_AARCH64
 #elif defined __arm__
     .incbin GHOSTEX_BOOTSTRAP_ARM
 #else
 # error Unsupported arch
 #endif
 1:
 blob_size:
     .int 1b - blob
