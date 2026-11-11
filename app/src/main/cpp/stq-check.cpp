#include "ggml-cpu/quants.h"
#include "ggml-quants.h"
#include <cstdio>
#include <cmath>
int main(){block_stq1_0 x[4];block_q8_K y[4];float values[1024];for(int test=0;test<12;test++){for(int n=0;n<1024;n++)values[n]=std::sin(n*.131f+test)*.7f;quantize_row_q8_K_ref(values,y,1024);for(int b=0;b<4;b++){for(int j=0;j<32;j++)x[b].qs[j]=(j*71+b*17+test*13)&255;for(int j=0;j<8;j++)x[b].sign[j]=(j*39+b*91+test*19)&255;x[b].d=ggml_fp32_to_fp16(.03f*(b+1));}float scalar=0,neon=0;ggml_vec_dot_stq1_0_q8_K_generic(1024,&scalar,0,x,0,y,0,1);ggml_vec_dot_stq1_0_q8_K(1024,&neon,0,x,0,y,0,1);std::printf("case=%d reference=%f neon=%f delta=%f\n",test,scalar,neon,neon-scalar);if(std::fabs(neon-scalar)>0.001f)return 2;}return 0;}
