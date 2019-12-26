Global VregArray[16]
Global I ; I value
Global PC ; program counter/ instruction pointer
Global SP ; stack pointer
Global Stack[16]
Global Sound_Timer
Global Delay_Timer
Global memory  ; the array that represents the RAM


decreaseTimer=CreateTimer(60) ; suprisingly easier than C#, create a 60Hz Timer

memory = CreateBank(4096) ; create the 4K of RAM

;;JUST FOR LOCAL TESTING


Function InitCPU(ROMImage)
RomSize = BankSize(ROMImage)
CopyBank(ROMImage,0,memory,512,RomSize)
PC = 512; set the program counter to the beginning of the bytes of ROM 0x200 or 512
Sound_Timer = 0
Delay_Timer = 0
I = 0
SP = 0  ; stack pointer starts at 0
Return 
End Function

Function EmulateCycle()
currentOPCode = (PeekByte(memory,PC) Shl 8)+PeekByte(memory,PC+1) ; get the 2 byte value stored at the program counter in memory
firstFourBits = currentOPCode And $F000  ; hex constants in blitz are gross, I guess I just have to pray this works
lastTwelveBits = currentOPCode And $0FFF
lastFourBits = currentOPCode And $000F
lastEightBits = PeekByte(memory,PC+1)  ;; it's just easier this way
VxRegIdentifier = ((currentOPCode And $0F00) Shr 8)  ; which index Vx actually is
VyRegIdentifier = ((currentOPCode And $00F0) Shr 4)  ; same for Vy
Print(currentOpCode)  ;; FOR DEBUG
Select firstFourBits
	Case $0000
		Select (currentOPCode And $000f)
			Case $0000  ;;  0xO0E0, clears screen
				;; do screen clearing things
				PC = PC + 2
			Case $000E  ;; 0x0EE, return from subroutine
				PC = Stack[SP]  ;; set program counter to value in stack
				SP = SP  - 1 ;; decrement stack pointer
		End Select
	Case $1000  ; 0x1NNN unconditional jump to address NNN
		PC=lastTwelveBits
	Case $2000  ;; 0x2NNN call subroutine at NNN (push PC to stack, increment pointer)
		Stack[SP] = PC
		SP = SP + 1
		PC = lastTwelveBits
	Case $3000  ; 0x3XNN skips the next instruction if Vx = NN
		If VregArray[VxRegIdentifier] = lastEightBits Then
			PC = PC +4  ;; skip next instruction
		Else
			PC = PC +2  ;; don't skip
		EndIf
	Case $4000  ; opcode 0x4XNN (skips next instruction if Vx != NN)
		If VregArray[VxRegIdentifier] <> lastEightBits Then
			PC = PC +4  ;; skip next instruction
		Else
			PC = PC +2  ;; don't skip
		EndIf
	Case $5000  ; opcode 0x5XY0 (skips next instruction if Vx == Vy)
		If VregArray[VxRegIdentifier] = VregArray[VyRegIdentifier] Then
			PC = PC +4  ;; skip next instruction
		Else
			PC = PC +2  ;; don't skip
		EndIf
	Case $6000  ; opcode 0x6XNN (sets Vx to NN)
		VregArray[VxRegIdentifier] = lastEightBits
		PC = PC + 2
	Case $7000  ; opcode 0x7NN (Vx += NN)
		VregArray[VxRegIdentifier] = VregArray[VxRegIdentifier] + lastEightBits
		PC = PC + 2
	Case $8000  ; there's a lot of 0x8000 codes
		Select lastFourBits
			Case $0  ; set Vx to Vy
				VregArray[VxRegIdentifier] = VregArray[VyRegIdentifier] 
				PC = PC+2;
			Case $1  ; set Vx to Vx OR Vy
				VregArray[VxRegIdentifier] = (VregArray[VxRegIdentifier] Or VregArray[VyRegIdentifier])
				PC = PC+2  ;; same thing but AND
			Case $2
				VregArray[VxRegIdentifier] = (VregArray[VxRegIdentifier] And VregArray[VyRegIdentifier])
				PC = PC+2  ;; same thing but XOR
			Case $3 
				VregArray[VxRegIdentifier] = (VregArray[VxRegIdentifier] Xor VregArray[VyRegIdentifier])
				PC = PC+2  ;; set Vx = Vx + Vy
			Case $4
				If VregArray[VyRegIdentifier] > ($ff - VregArray[VxRegIdentifier])  Then;; set carry flag true
					VregArray[15] = $1
				Else
					VregArray[15] = $0
				EndIf
				VregArray[VxRegIdentifier] = VregArray[VxRegIdentifier] + VregArray[VyRegIdentifier] 
				PC = PC + 2
			Case $5
				If VregArray[VxRegIdentifier] <= VregArray[VyRegIdentifier] Then
					VregArray[15] = $1
				Else
					VregArray[15] = $0
				EndIf
				VregArray[VxRegIdentifier]  = VregArray[VxRegIdentifier] - VregArray[VyRegIdentifier]
				PC = PC+2
			Case $6  ;; store LSB of Vx in Vf, shift Vx to the right by 1
			 	VregArray[15] = (VregArray[VxRegIdentifier] And $1)
				VregArray[VxRegIdentifier] = (VregArray[VxRegIdentifier] Shr $1)
				PC = PC+2
			Case $7
				If VregArray[VxRegIdentifier] <= VregArray[VyRegIdentifier] Then
					VregArray[15] = $1
				Else
					VregArray[15] = $0
				EndIf
				VregArray[VxRegIdentifier] = VregArray[VxRegIdentifier] - VregArray[VyRegIdentifier]
				PC = PC +2
  			Case $E  ;; store MSB of Vx in Vf, shift Vx to the left by 1
				VregArray[15] = ((VregArray[VxRegIdentifier] Shr 7) And $80)
				VregArray[VxRegIdentifier] = (VregArray[VxRegIdentifier] Shl 1)
				PC = PC + 2
			Default
				;; how do you throw errors in blitz????????
			End Select
		Case $9000  ;; opcode 0x9XY0, skips next instruction if Vx != Vy
			If VregArray[VxRegIdentifier] <> VregArray[VyRegIdentifier]	Then
				PC = PC + 4
			Else
				PC = PC + 2
			EndIf 
		Case $A000  ;; opcode 0xANNN, set I to NNN
			I = lastTwelveBits
			PC = PC + 2
		Case $B000  ;; opcode 0xBNNN, jump to address V_0 + NNN
			PC = (lastTwelveBits + VregArray[0])
		Case $C000  ;; opcode 0xCXNN, set Vx to the result of a bitwise AND operation of a random number and NN
			VregArray[VxRegIdentifier] = (Rand(0,255) And lastEightBits)
			PC = PC + 2
		Case $D000 ;; opcode DXYN, draws a sprite at coords (Vx,Vy) that has width of 8 pixels and a height of N pixels
			PC = PC + 2  ;; currently does nothing
		Case $E000
			Select lastEightBits
				Case $9E  ;; skips the next instruction if the key stored in Vx is pressed
					If(KeyDown(VregArray[VxRedIdentifier])) Then 
						PC = PC + 4
					Else 
						PC = PC + 2
					EndIf
				Case $A1  ;; skips the next instruction if the key stored in Vx isn't pressed
					If(Not (KeyDown(VregArray[VxRedIdentifier]))) Then   ;; wow it's really just NOT
						PC = PC + 4
					Else 
						PC = PC + 2
					EndIf
				Default
					;; idek what to do in this situation
				End Select
		Case $F000  ;; opcode 0xFXNN
			Select lastEightBits
				Case $07  ;; gets the display timer value, and stores it in Vx
					VregArray[VxRegIdentifier] = Display_Timer
					PC = PC + 2
				Case $0A  ;; A key press is awaited and stored in Vx  (USE WAITKEY() and some sort of mapping)
					keyPresed = False
					;;While(Not keyPressed)
					;; actually implement this
					PC = PC + 2
				Case $15  ;; set delay timer to Vx
					Delay_Timer	= VregArray[VxRegIdentifier]
					PC = PC + 2
				Case $18  ;; set sount timer to Vx
					Sound_Timer = VregArray[VxRegIdentifier]
					PC = PC + 2
				Case $1E  ;; adds Vx to I (not doing the one undocumented thing)
					I = I + VregArray[VxRegIdentifier]
					PC = PC + 2
				Case $29  ;; sets I to the location of the sprite for the character in Vx
					I = VregArray[VregIdentifier] * 5
					PC = PC + 2
				Case $33  ;; binary coded decimal stuff
					PokeByte(memory,I,(VregArray[VxRegIdentifier]/100))  ;; have to use PokeByte to write to memory
					PokeByte(memory,I+1,((VregArray[VxRegIdentifier]/10) Mod 10))
					PokeByte(memory,I+2,((VregArray[VxRegIdentifier]Mod 100) Mod 10))
					PC = PC + 2  ;; forgot to put the "increment program counter line" and the program got stuck here, that means it's working!!!! 
				Case $55  ;; stores V0 to Vx in memory starting from address I
					For t=0 To VregArray[VxRegIdentifier]
					PokeByte(memory,I+t,VregArray[t])
					;memory[I+t] = VregArray[t]
					Next
					PC = PC + 2
				Case $65  ;;  fils V0 to Vx from values in memory starting from address I
					For t=0 To VregArray[VxRegIdentifier]
					VregArray[t] = PeekByte(memory,I+t);memory[I+t] 
					Next
					PC = PC + 2
				End Select
Default
End Select
End Function