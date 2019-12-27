;; Copyright 2019 Maurice Montag
;; See LICENSE file for more information

Include "CPU.bb"

Const FileName$ = "test.ch8"


FileHandle = OpenFile("test.ch8")
ROMSize = FileSize(FileName)
Print(ROMSize)
ROMBuffer = CreateBank(ROMSize)
ReadBytes(ROMBuffer,FileHandle,0,ROMSize)  ;; copy the bytes from the ROM file into our buffer
CloseFile(FileHandle)
For t=0 To ROMSize-1
;Print(PeekByte(ROMBuffer,t))
Next 
InitCPU(ROMBuffer)  ;; then load our buffer into our CPU's memory
While(Not KeyDown(1))
EmulateCycle()  ;; then emulate cycles
Delay(500)
Wend


